package dev.agenticwallet.spike.backend.skeleton;

import dev.agenticwallet.spike.backend.support.OffsetClock;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * Reads and set-ups the scenarios share; plain SQL, because the tests check the stored facts. Built
 * by the scenario configuration, not scanned, so Spring Modulith never sees it as a module.
 */
class SkeletonFixtures {

    private static final int MONTHS_PER_TEST = 31;

    private final JdbcClient jdbc;
    private final OffsetClock clock;
    private final RestClient simulators;

    SkeletonFixtures(
            JdbcClient jdbc, OffsetClock clock, RestClient.Builder builder, String simulatorsUrl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.simulators = builder.baseUrl(simulatorsUrl).build();
    }

    /** Each scenario moves the clock into a month of its own, so budget periods never collide. */
    void ownBudgetPeriod(int scenarioNumber) {
        clock.shiftBy(Duration.ofDays((long) MONTHS_PER_TEST * scenarioNumber));
    }

    /** A second mandate for scenarios that change a mandate; SYNTHETIC terms. */
    MandateId newMandate(String limit, String budget) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "insert into mandates.mandate (id, version, status, limit_per_purchase,"
                                + " budget, currency) values (:id, 1, 'ACTIVE', :limit, :budget,"
                                + " 'EUR')")
                .param("id", id)
                .param("limit", new BigDecimal(limit))
                .param("budget", new BigDecimal(budget))
                .update();
        return new MandateId(id);
    }

    AttemptState attemptState(DecisionId decision) {
        return AttemptState.valueOf(
                jdbc.sql("select state from payments.checkout_attempt where decision_id = :d")
                        .param("d", decision.value())
                        .query(String.class)
                        .single());
    }

    String reservationState(DecisionId decision) {
        return jdbc.sql("select state from ledger.reservation where decision_id = :d")
                .param("d", decision.value())
                .query(String.class)
                .single();
    }

    Map<String, Object> budgetPeriod(MandateId mandate) {
        return jdbc.sql(
                        "select reserved, counted from ledger.budget_period where mandate_id = :m"
                                + " order by period_start desc limit 1")
                .param("m", mandate.value())
                .query()
                .singleRow();
    }

    String preApprovalState(DecisionId decision) {
        return jdbc.sql("select state from authorisation.pre_approval where decision_id = :d")
                .param("d", decision.value())
                .query(String.class)
                .single();
    }

    int proposalsWithKey(String purchaseKey) {
        return jdbc.sql("select count(*) from decisioning.proposal where purchase_key = :k")
                .param("k", purchaseKey)
                .query(Integer.class)
                .single();
    }

    int incompletePublications() {
        return jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Integer.class)
                .single();
    }

    /**
     * Outbox rows of one event type for a decision: durable proof that a state was passed through.
     */
    int publicationsOf(String eventSimpleName, DecisionId decision) {
        return jdbc.sql(
                        "select count(*) from event_publication where event_type like :type and"
                                + " serialized_event like :decision")
                .param("type", "%" + eventSimpleName)
                .param("decision", "%" + decision.value() + "%")
                .query(Integer.class)
                .single();
    }

    /** How often the registry delivered one event type for a decision to its listener. */
    int completionAttemptsOf(String eventSimpleName, DecisionId decision) {
        return jdbc.sql(
                        "select coalesce(max(completion_attempts), 0) from event_publication where"
                                + " event_type like :type and serialized_event like :decision")
                .param("type", "%" + eventSimpleName)
                .param("decision", "%" + decision.value() + "%")
                .query(Integer.class)
                .single();
    }

    int credentialRequestsFor(DecisionId decision) {
        Map<?, ?> count =
                simulators
                        .get()
                        .uri("/network/credentials/{d}/requests", decision.value())
                        .retrieve()
                        .body(Map.class);
        return (Integer) count.get("requests");
    }

    void delayMerchantCompletionsBy(Duration delay) {
        simulators
                .post()
                .uri("/control/completion-delay")
                .body(Map.of("completionDelayMillis", delay.toMillis()))
                .retrieve()
                .toBodilessEntity();
    }

    void resetMerchant() {
        simulators.post().uri("/control/reset").retrieve().toBodilessEntity();
    }
}
