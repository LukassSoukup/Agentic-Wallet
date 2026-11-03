package dev.agenticwallet.spike.backend.decisioning;

import dev.agenticwallet.spike.core.decisioning.Proposal;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.UntrustedText;
import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantItemId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import dev.agenticwallet.spike.kernel.ids.PurchaseKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proposals by (agent instance, purchaseKey), the unique key of 06-consistency §5. The insert of a
 * key already in flight blocks until the other transaction ends and then fails, which is what makes
 * two concurrent deliveries one decision.
 */
@Component
class ProposalRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ProposalRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<StoredProposal> find(AgentInstanceId agent, PurchaseKey key) {
        return jdbc.sql(
                        "select agent_instance_id, purchase_key, mandate_id, merchant_reference,"
                                + " lines, description, decision_id from decisioning.proposal where"
                                + " agent_instance_id = :agent and purchase_key = :key")
                .param("agent", agent.value())
                .param("key", key.value())
                .query((row, n) ->
                        new StoredProposal(
                                new Proposal(
                                        new AgentInstanceId(
                                                row.getObject("agent_instance_id", UUID.class)),
                                        PurchaseKey.parse(row.getString("purchase_key")),
                                        new MandateId(row.getObject("mandate_id", UUID.class)),
                                        MerchantReference.parse(
                                                row.getString("merchant_reference")),
                                        readLines(row.getString("lines")),
                                        UntrustedText.of(row.getString("description"))),
                                new DecisionId(row.getObject("decision_id", UUID.class))))
                .optional();
    }

    /** Throws {@link DuplicateKeyException} when the key exists; the caller re-reads the twin. */
    @Transactional(propagation = Propagation.MANDATORY)
    void insert(Proposal proposal, DecisionId decision, Instant receivedAt) {
        jdbc.sql(
                        "insert into decisioning.proposal (id, agent_instance_id, purchase_key,"
                            + " mandate_id, merchant_reference, lines, description, decision_id,"
                            + " received_at) values (:id, :agent, :key, :mandate, :merchant,"
                            + " cast(:lines as jsonb), :description, :decision, :at)")
                .param("id", UUID.randomUUID())
                .param("agent", proposal.agentInstance().value())
                .param("key", proposal.purchaseKey().value())
                .param("mandate", proposal.mandateId().value())
                .param("merchant", proposal.merchant().value())
                .param(
                        "lines",
                        json.writeValueAsString(
                                proposal.lines().stream().map(LineJson::of).toList()))
                .param("description", proposal.description().content())
                .param("decision", decision.value())
                .param("at", Timestamp.from(receivedAt))
                .update();
    }

    private List<OrderLine> readLines(String lines) {
        return json.readValue(lines, new TypeReference<List<LineJson>>() {}).stream()
                .map(LineJson::toLine)
                .toList();
    }

    /** The JSON shape of a stored line; the kernel's record carries no annotations (B7). */
    record LineJson(String merchantItemId, int quantity) {
        static LineJson of(OrderLine line) {
            return new LineJson(line.merchantItemId().value(), line.quantity());
        }

        OrderLine toLine() {
            return new OrderLine(MerchantItemId.parse(merchantItemId), quantity);
        }
    }
}
