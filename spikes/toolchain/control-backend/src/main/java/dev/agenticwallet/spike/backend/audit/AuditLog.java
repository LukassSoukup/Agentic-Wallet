package dev.agenticwallet.spike.backend.audit;

import dev.agenticwallet.spike.core.decisioning.DecisionInput;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Published API of {@code audit} (invariant 8): the decision record with its complete {@code
 * DecisionInput}, written in the decision's transaction ({@code MANDATORY}) into an insert-only
 * table, and read back for replay. JSON happens here, in the adapter, never in the core.
 */
@Component
public class AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    AuditLog(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AuditEntry entry) {
        jdbc.sql(
                        "insert into audit.decision_record (decision_id, cardholder_id,"
                            + " agent_instance_id, outcome, reasons, schema_version,"
                            + " rule_set_version, decision_input, written_at) values (:decision,"
                            + " :cardholder, :agent, :outcome, :reasons, :schema, :rules,"
                            + " cast(:input as jsonb), :at)")
                .param("decision", entry.decisionId().value())
                .param("cardholder", entry.cardholderId())
                .param("agent", entry.agentInstance().value())
                .param("outcome", entry.verdict().outcome().name())
                .param("reasons", joinReasons(entry.verdict()))
                .param("schema", entry.input().schemaVersion())
                .param("rules", entry.input().ruleSetVersion())
                .param("input", json.writeValueAsString(entry.input()))
                .param("at", Timestamp.from(clock.now()))
                .update();
    }

    /** The replay unit as stored: {@code Decider.decide(input)} must equal {@code verdict}. */
    public Optional<StoredDecision> read(DecisionId decision) {
        return jdbc.sql(
                        "select outcome, reasons, decision_input from audit.decision_record where"
                                + " decision_id = :decision")
                .param("decision", decision.value())
                .query((row, n) ->
                        new StoredDecision(
                                json.readValue(
                                        row.getString("decision_input"), DecisionInput.class),
                                new Verdict(
                                        Outcome.valueOf(row.getString("outcome")),
                                        Arrays.stream(row.getString("reasons").split(","))
                                                .map(ReasonCode::valueOf)
                                                .toList())))
                .optional();
    }

    public record StoredDecision(DecisionInput input, Verdict verdict) {}

    private static String joinReasons(Verdict verdict) {
        return verdict.reasons().stream().map(ReasonCode::name).collect(Collectors.joining(","));
    }
}
