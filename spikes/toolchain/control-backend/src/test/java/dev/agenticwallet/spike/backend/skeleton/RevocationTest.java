package dev.agenticwallet.spike.backend.skeleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.agenticwallet.spike.backend.decisioning.ProposalOutcome;
import dev.agenticwallet.spike.backend.decisioning.ProposalService;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.backend.mandates.Mandates;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The payments ↔ mandates pair through kernel events (05-open-items): revocation reaches payments
 * and cancels nothing that already asked for a credential; a later proposal is declined on the
 * status in its input.
 */
class RevocationTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired Mandates mandates;
    @Autowired SkeletonFixtures fixtures;

    private final CallerIdentity caller = new CallerIdentity(Proposals.CARDHOLDER, Proposals.AGENT);

    @Test
    void revocationLeavesACompletedAttemptAloneAndDeclinesTheNextProposal() {
        fixtures.ownBudgetPeriod(8);
        MandateId mandate = fixtures.newMandate("100.00", "150.00");
        var approved =
                (ProposalOutcome.Decided)
                        proposals.propose(
                                caller,
                                Proposals.of(mandate, Proposals.KETTLE, 1, "before revocation"));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(approved.decisionId()))
                                .isEqualTo(AttemptState.COMPLETED));

        mandates.revoke(mandate);

        var declined =
                (ProposalOutcome.Decided)
                        proposals.propose(
                                caller,
                                Proposals.of(mandate, Proposals.KETTLE, 1, "after revocation"));
        assertThat(declined.verdict().outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(declined.verdict().reasons()).contains(ReasonCode.MANDATE_NOT_ACTIVE);
        assertThat(fixtures.attemptState(approved.decisionId())).isEqualTo(AttemptState.COMPLETED);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(fixtures.incompletePublications()).isZero());
    }
}
