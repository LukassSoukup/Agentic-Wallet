package dev.agenticwallet.spike.backend.mandates;

import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.events.CheckoutCompleted;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * {@code mandates} listens to {@code payments} through a kernel event (the pair that must stay
 * acyclic in code, 05-open-items). Idempotent on the attempt id: a redelivery counts nothing.
 */
@Component
class CompletedPurchaseListener {

    private final JdbcClient jdbc;
    private final Clock clock;

    CompletedPurchaseListener(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(CheckoutCompleted event) {
        jdbc.sql(
                        "insert into mandates.completed_purchase (attempt_id, mandate_id,"
                            + " decision_id, completed_at) values (:attempt, :mandate, :decision,"
                            + " :at) on conflict (attempt_id) do nothing")
                .param("attempt", event.attemptId())
                .param("mandate", event.mandateId())
                .param("decision", event.decisionId())
                .param("at", java.sql.Timestamp.from(clock.now()))
                .update();
    }
}
