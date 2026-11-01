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
 * UNKNOWN (announced by its own event), never FAILED; the reservation and pre-approval stay; the
 * resolver reads the session status and settles it as COMPLETED, counted once, one credential.
 */
class CheckoutTimeoutTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired SkeletonFixtures fixtures;
    @Autowired RecordedCheckoutEvents events;

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

        // UNKNOWN lasts only until the resolver's next tick (the merchant did complete), so the
        // evidence is the CheckoutOutcomeUnknown event reaching a listener, not a glimpse of the
        // state.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(decided.decisionId()))
                                .isEqualTo(AttemptState.COMPLETED));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertThat(events.unknownDecisions())
                            .containsOnlyOnce(decided.decisionId().value());
                    assertThat(events.completedDecisions())
                            .containsOnlyOnce(decided.decisionId().value());
                });
        assertThat(events.failedDecisions()).doesNotContain(decided.decisionId().value());
        assertThat(fixtures.publicationsOf("CheckoutOutcomeUnknown", decided.decisionId()))
                .isEqualTo(1);
        assertThat(fixtures.reservationState(decided.decisionId())).isEqualTo("COUNTED");
        assertThat(fixtures.preApprovalState(decided.decisionId())).isEqualTo("ACTIVE");
        assertThat(fixtures.credentialRequestsFor(decided.decisionId())).isEqualTo(1);
    }
}
