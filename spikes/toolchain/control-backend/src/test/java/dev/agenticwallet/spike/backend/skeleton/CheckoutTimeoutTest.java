package dev.agenticwallet.spike.backend.skeleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.agenticwallet.spike.backend.decisioning.ProposalOutcome;
import dev.agenticwallet.spike.backend.decisioning.ProposalService;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 06-consistency §3: the merchant completes but answers after our read timeout. The attempt goes to
 * UNKNOWN, never FAILED; the reservation and pre-approval stay; the resolver reads the session
 * status after the stored deadline and settles it as COMPLETED, counted once, one credential.
 */
class CheckoutTimeoutTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired SkeletonFixtures fixtures;

    private final CallerIdentity caller = new CallerIdentity(Proposals.CARDHOLDER, Proposals.AGENT);

    @AfterEach
    void merchantAnswersPromptlyAgain() {
        fixtures.resetMerchant();
    }

    @Test
    void aLateMerchantAnswerIsUnknownThenResolvedCompleted() {
        fixtures.ownBudgetPeriod(6);
        MandateId mandate = fixtures.newMandate("100.00", "150.00");
        fixtures.delayMerchantCompletionsBy(Duration.ofSeconds(3));

        var decided =
                (ProposalOutcome.Decided)
                        proposals.propose(
                                caller, Proposals.of(mandate, Proposals.KETTLE, 1, "slow kettle"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(decided.decisionId()))
                                .isEqualTo(AttemptState.UNKNOWN));
        assertThat(fixtures.reservationState(decided.decisionId())).isEqualTo("RESERVED");
        assertThat(fixtures.preApprovalState(decided.decisionId())).isEqualTo("ACTIVE");

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(decided.decisionId()))
                                .isEqualTo(AttemptState.COMPLETED));
        assertThat(fixtures.reservationState(decided.decisionId())).isEqualTo("COUNTED");
        assertThat(fixtures.credentialRequestsFor(decided.decisionId())).isEqualTo(1);
    }
}
