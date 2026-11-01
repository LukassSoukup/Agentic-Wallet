package dev.agenticwallet.spike.hook;

import dev.agenticwallet.spike.core.decisioning.Outcome;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the published view with plain SQL on the hook's own small pool (ADR-0009 §A). */
@Component
class PreApprovalView {

    private final JdbcClient jdbc;

    PreApprovalView(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Outcome> outcomeOf(UUID decisionId) {
        return jdbc.sql("select outcome from decisioning.decision where id = :id")
                .param("id", decisionId)
                .query(String.class)
                .optional()
                .map(Outcome::valueOf);
    }
}
