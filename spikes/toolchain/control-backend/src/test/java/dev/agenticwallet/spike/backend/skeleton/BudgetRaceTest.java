package dev.agenticwallet.spike.backend.skeleton;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.backend.decisioning.ProposalOutcome;
import dev.agenticwallet.spike.backend.decisioning.ProposalService;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 06-consistency §1: two proposals that each fit the budget but not together, released at the same
 * instant. The budget-period row lock serialises them: exactly one APPROVE, the other DECLINE on
 * the updated ledger.
 */
class BudgetRaceTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired SkeletonFixtures fixtures;

    private final CallerIdentity caller = new CallerIdentity(Proposals.CARDHOLDER, Proposals.AGENT);

    @Test
    void twoProposalsRacingForOneBudgetGetExactlyOneApprove() throws Exception {
        fixtures.ownBudgetPeriod(3);
        MandateId mandate = fixtures.newMandate("100.00", "150.00");
        var first = Proposals.of(mandate, Proposals.FLIGHT, 1, "flight one");
        var second = Proposals.of(mandate, Proposals.FLIGHT, 1, "flight two");

        List<ProposalOutcome.Decided> outcomes = raceToDecide(List.of(first, second));

        assertThat(outcomes)
                .extracting(d -> d.verdict().outcome())
                .containsExactlyInAnyOrder(Outcome.APPROVE, Outcome.DECLINE);
        var declined =
                outcomes.stream()
                        .filter(d -> d.verdict().outcome() == Outcome.DECLINE)
                        .findFirst()
                        .orElseThrow();
        assertThat(declined.verdict().reasons()).contains(ReasonCode.OVER_BUDGET);
        assertThat(
                        ((java.math.BigDecimal) fixtures.budgetPeriod(mandate).get("reserved"))
                                .add(
                                        (java.math.BigDecimal)
                                                fixtures.budgetPeriod(mandate).get("counted")))
                .isEqualByComparingTo("89.00");
    }

    private List<ProposalOutcome.Decided> raceToDecide(
            List<dev.agenticwallet.spike.core.decisioning.Proposal> racing) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(racing.size())) {
            List<Future<ProposalOutcome>> futures =
                    racing.stream()
                            .map(proposal ->
                                    pool.submit(() -> {
                                        start.await();
                                        return proposals.propose(caller, proposal);
                                    }))
                            .toList();
            start.countDown();
            return futures.stream()
                    .map(BudgetRaceTest::outcomeOf)
                    .map(ProposalOutcome.Decided.class::cast)
                    .toList();
        }
    }

    private static ProposalOutcome outcomeOf(Future<ProposalOutcome> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while racing", interrupted);
        } catch (java.util.concurrent.ExecutionException failed) {
            throw new IllegalStateException("a racing proposal failed", failed.getCause());
        }
    }
}
