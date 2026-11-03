package dev.agenticwallet.spike.backend.skeleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.agenticwallet.spike.backend.audit.AuditLog;
import dev.agenticwallet.spike.backend.decisioning.ProposalOutcome;
import dev.agenticwallet.spike.backend.decisioning.ProposalService;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.backend.mandates.Mandates;
import dev.agenticwallet.spike.core.decisioning.Decider;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.payments.AttemptState;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * One trivial purchase end to end inside the backend: proposal → session at the demo shop → decide
 * → one transaction → outbox → checkout attempt → merchant completed → counted, with the audit
 * record replaying to the same verdict (invariants 1, 5, 8).
 */
class PurchaseEndToEndTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired AuditLog audit;
    @Autowired Mandates mandates;
    @Autowired SkeletonFixtures fixtures;

    private final CallerIdentity caller = new CallerIdentity(Proposals.CARDHOLDER, Proposals.AGENT);

    @Test
    void approvesPaysOnceAndReplays() {
        fixtures.ownBudgetPeriod(1);
        var proposal =
                Proposals.of(Proposals.SEEDED_MANDATE, Proposals.KETTLE, 1, "a synthetic kettle");

        ProposalOutcome outcome = proposals.propose(caller, proposal);

        var decided = (ProposalOutcome.Decided) outcome;
        assertThat(decided.verdict().outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(decided.verdict().reasons())
                .containsExactly(
                        ReasonCode.MANDATE_ACTIVE,
                        ReasonCode.WITHIN_LIMIT,
                        ReasonCode.WITHIN_BUDGET);
        assertThat(decided.redelivered()).isFalse();
        assertThat(fixtures.preApprovalState(decided.decisionId())).isEqualTo("ACTIVE");

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(decided.decisionId()))
                                .isEqualTo(AttemptState.COMPLETED));
        assertThat(fixtures.reservationState(decided.decisionId())).isEqualTo("COUNTED");
        assertThat(fixtures.budgetPeriod(Proposals.SEEDED_MANDATE))
                .satisfies(period -> {
                    assertThat((BigDecimal) period.get("reserved")).isEqualByComparingTo("0.00");
                    assertThat((BigDecimal) period.get("counted")).isEqualByComparingTo("29.90");
                });
        assertThat(fixtures.credentialRequestsFor(decided.decisionId())).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertThat(mandates.completedPurchasesOf(Proposals.SEEDED_MANDATE))
                                .isEqualTo(1));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(fixtures.incompletePublications()).isZero());

        var stored = audit.read(decided.decisionId()).orElseThrow();
        assertThat(stored.verdict()).isEqualTo(decided.verdict());
        assertThat(Decider.decide(stored.input())).isEqualTo(stored.verdict());
        assertThat(stored.input().proposal().description().content())
                .isEqualTo("a synthetic kettle");
        assertThat(stored.input().cart().total().amount()).isEqualByComparingTo("29.90");
    }

    @Test
    void declinesOverTheLimitWithoutReservingOrPaying() {
        fixtures.ownBudgetPeriod(2);
        var proposal =
                Proposals.of(
                        Proposals.SEEDED_MANDATE, Proposals.FLIGHT, 2, "two synthetic flights");

        var decided = (ProposalOutcome.Decided) proposals.propose(caller, proposal);

        assertThat(decided.verdict().outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(decided.verdict().reasons()).contains(ReasonCode.OVER_LIMIT);
        assertThat(fixtures.budgetPeriod(Proposals.SEEDED_MANDATE))
                .satisfies(period ->
                        assertThat((BigDecimal) period.get("reserved"))
                                .isEqualByComparingTo("0.00"));
        assertThat(fixtures.credentialRequestsFor(decided.decisionId())).isZero();
        assertThat(audit.read(decided.decisionId())).isPresent();
    }
}
