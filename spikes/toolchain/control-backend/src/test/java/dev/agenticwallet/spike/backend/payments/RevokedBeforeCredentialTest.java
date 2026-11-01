package dev.agenticwallet.spike.backend.payments;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.backend.mandates.Mandates;
import dev.agenticwallet.spike.backend.skeleton.SkeletonBackendTest;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * The revocation race (T-037 review finding): a mandate revoked after the decision committed but
 * before the attempt asked for a credential ends in CANCELLED, with no credential request. The
 * listener's first transition re-reads the mandate under a share lock.
 */
class RevokedBeforeCredentialTest extends SkeletonBackendTest {

    @Autowired CheckoutAttemptService attempts;
    @Autowired Mandates mandates;
    @Autowired JdbcClient jdbc;
    @Autowired RestClient.Builder restClientBuilder;

    @Test
    void aDecisionMadeForAMandateRevokedSinceIsCancelledBeforeAnyCredential() {
        UUID mandate = UUID.randomUUID();
        jdbc.sql(
                        "insert into mandates.mandate (id, version, status, limit_per_purchase,"
                            + " budget, currency) values (:id, 1, 'ACTIVE', 100.00, 150.00, 'EUR')")
                .param("id", mandate)
                .update();
        mandates.revoke(new dev.agenticwallet.spike.kernel.ids.MandateId(mandate));
        DecisionId decision = new DecisionId(UUID.randomUUID());
        DecisionMade approvedBeforeTheRevocation =
                new DecisionMade(
                        decision.value(),
                        mandate,
                        "APPROVE",
                        Money.of("29.90", "EUR"),
                        "demo-shop",
                        "syn-session-revoked");

        attempts.runFor(approvedBeforeTheRevocation);

        String state =
                jdbc.sql("select state from payments.checkout_attempt where decision_id = :d")
                        .param("d", decision.value())
                        .query(String.class)
                        .single();
        assertThat(state).isEqualTo(AttemptState.CANCELLED.name());
        Map<?, ?> requests =
                restClientBuilder
                        .baseUrl(dev.agenticwallet.spike.backend.support.TestSimulators.URL)
                        .build()
                        .get()
                        .uri("/network/credentials/{d}/requests", decision.value())
                        .retrieve()
                        .body(Map.class);
        assertThat(requests.get("requests")).isEqualTo(0);
    }
}
