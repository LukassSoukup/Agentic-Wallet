package dev.agenticwallet.spike.backend.audit;

import dev.agenticwallet.spike.kernel.events.DecisionMade;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Consumes the outbox: one audit row per decision, inside its own transaction. */
@Component
class AuditListener {

    private final JdbcClient jdbc;

    AuditListener(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @ApplicationModuleListener
    void on(DecisionMade event) {
        jdbc.sql(
                        "insert into audit.audit_event (decision_id, outcome, schema_version)"
                                + " values (:id, :outcome, :version) on conflict do nothing")
                .param("id", event.decisionId())
                .param("outcome", event.outcome())
                .param("version", event.schemaVersion())
                .update();
    }
}
