package dev.agenticwallet.spike.backend.skeleton;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.backend.decisioning.ProposalOutcome;
import dev.agenticwallet.spike.backend.decisioning.ProposalService;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.core.decisioning.Proposal;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.PurchaseKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 06-consistency §5: one key, one decision, however often and however concurrently it arrives. */
class DuplicateDeliveryTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired SkeletonFixtures fixtures;

    private final CallerIdentity caller = new CallerIdentity(Proposals.CARDHOLDER, Proposals.AGENT);

    @Test
    void theSamePurchaseKeyDeliveredTwiceAtOnceYieldsOneDecisionAndOneProposalRow()
            throws Exception {
        fixtures.ownBudgetPeriod(4);
        MandateId mandate = fixtures.newMandate("100.00", "150.00");
        PurchaseKey key = PurchaseKey.parse("twin-" + UUID.randomUUID());
        Proposal proposal =
                Proposals.withKey(Proposals.of(mandate, Proposals.KETTLE, 1, "kettle"), key);

        List<ProposalOutcome.Decided> both = deliverConcurrently(proposal, proposal);

        assertThat(both)
                .extracting(ProposalOutcome.Decided::decisionId)
                .containsOnly(both.getFirst().decisionId());
        assertThat(both)
                .extracting(ProposalOutcome.Decided::verdict)
                .containsOnly(both.getFirst().verdict());
        assertThat(both)
                .extracting(ProposalOutcome.Decided::redelivered)
                .containsExactlyInAnyOrder(false, true);
        assertThat(fixtures.proposalsWithKey(key.value())).isEqualTo(1);
    }

    @Test
    void aRewordedRetryGetsTheStoredDecisionAndDifferentBindingFieldsAreAConflict() {
        fixtures.ownBudgetPeriod(5);
        MandateId mandate = fixtures.newMandate("100.00", "150.00");
        Proposal proposal = Proposals.of(mandate, Proposals.KETTLE, 1, "the kettle");
        var first = (ProposalOutcome.Decided) proposals.propose(caller, proposal);

        var retried =
                (ProposalOutcome.Decided)
                        proposals.propose(
                                caller, Proposals.reworded(proposal, "that kettle, please"));
        ProposalOutcome conflict =
                proposals.propose(
                        caller,
                        Proposals.withKey(
                                Proposals.of(mandate, Proposals.KETTLE, 2, "two kettles"),
                                proposal.purchaseKey()));

        assertThat(retried.decisionId()).isEqualTo(first.decisionId());
        assertThat(retried.redelivered()).isTrue();
        assertThat(conflict).isInstanceOf(ProposalOutcome.PurchaseKeyConflict.class);
        assertThat(fixtures.proposalsWithKey(proposal.purchaseKey().value())).isEqualTo(1);
    }

    private List<ProposalOutcome.Decided> deliverConcurrently(Proposal... deliveries)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(deliveries.length)) {
            List<Future<ProposalOutcome>> futures =
                    java.util.Arrays.stream(deliveries)
                            .map(delivery ->
                                    pool.submit(() -> {
                                        start.await();
                                        return proposals.propose(caller, delivery);
                                    }))
                            .toList();
            start.countDown();
            List<ProposalOutcome.Decided> decided = new java.util.ArrayList<>();
            for (Future<ProposalOutcome> future : futures) {
                decided.add((ProposalOutcome.Decided) resultOf(future));
            }
            return decided;
        }
    }

    private static ProposalOutcome resultOf(Future<ProposalOutcome> future)
            throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException failed) {
            throw new IllegalStateException("a delivery failed", failed.getCause());
        }
    }
}
