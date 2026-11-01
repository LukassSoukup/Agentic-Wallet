package dev.agenticwallet.spike.backend.skeleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.agenticwallet.spike.backend.decisioning.ProposalOutcome;
import dev.agenticwallet.spike.backend.decisioning.ProposalService;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

/**
 * ADR-0009 B6: a redelivery of {@code DecisionMade} while the first listener is mid-call (the
 * merchant answers late) changes nothing: the attempt is past CREATED, so the second delivery
 * returns, the credential is requested once, and the first run's outcome stands.
 */
class RedeliveryFenceTest extends SkeletonBackendTest {

    @Autowired ProposalService proposals;
    @Autowired IncompleteEventPublications incomplete;
    @Autowired SkeletonFixtures fixtures;

    private final CallerIdentity caller = new CallerIdentity(Proposals.CARDHOLDER, Proposals.AGENT);

    @AfterEach
    void merchantAnswersPromptlyAgain() {
        fixtures.resetMerchant();
    }

    @Test
    void resubmittingTheIncompletePublicationMidCallChangesNothing() {
        fixtures.ownBudgetPeriod(7);
        MandateId mandate = fixtures.newMandate("100.00", "150.00");
        fixtures.delayMerchantCompletionsBy(Duration.ofSeconds(3));
        var decided =
                (ProposalOutcome.Decided)
                        proposals.propose(
                                caller,
                                Proposals.of(mandate, Proposals.KETTLE, 1, "fenced kettle"));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(decided.decisionId()))
                                .isEqualTo(AttemptState.COMPLETING));

        incomplete.resubmitIncompletePublications(publication ->
                publication.getEvent() instanceof DecisionMade made
                        && made.decisionId().equals(decided.decisionId().value()));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(fixtures.attemptState(decided.decisionId()))
                                .isEqualTo(AttemptState.COMPLETED));
        assertThat(fixtures.credentialRequestsFor(decided.decisionId())).isEqualTo(1);
        assertThat(fixtures.reservationState(decided.decisionId())).isEqualTo("COUNTED");
    }
}
