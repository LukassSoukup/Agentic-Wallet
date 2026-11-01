package dev.agenticwallet.spike.harness;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The scripted driver of T-037 (a test harness, not a shopping agent): with the {@code skeleton}
 * profile it proposes one SYNTHETIC purchase through {@code control-mcp} and polls the decision
 * until the checkout settles. No model is involved; the LLM is not on the decision path.
 */
@Component
@Profile("skeleton")
class ScriptedPurchase implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScriptedPurchase.class);
    private static final Set<String> SETTLED =
            Set.of("COMPLETED", "FAILED", "UNKNOWN", "CANCELLED");
    private static final Duration POLL_FOR = Duration.ofSeconds(60);

    private final McpSyncClient control;
    private final ScriptedPurchaseProperties purchase;

    ScriptedPurchase(List<McpSyncClient> clients, ScriptedPurchaseProperties purchase) {
        if (clients.size() != 1) {
            throw new IllegalStateException(
                    "the harness connects exactly one control MCP server in the skeleton");
        }
        this.control = clients.getFirst();
        this.purchase = purchase;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        String purchaseKey = UUID.randomUUID().toString();
        Map<String, Object> proposal =
                Map.of(
                        "purchaseKey", purchaseKey,
                        "mandateId", purchase.mandateId(),
                        "merchantReference", purchase.merchantReference(),
                        "lines",
                                List.of(
                                        Map.of(
                                                "merchantItemId",
                                                purchase.merchantItemId(),
                                                "quantity",
                                                purchase.quantity())),
                        "description", "scripted synthetic purchase");
        Instant started = Instant.now();
        McpSchema.CallToolResult proposed =
                control.callTool(
                        McpSchema.CallToolRequest.builder("propose_purchase")
                                .arguments(proposal)
                                .build());
        long proposeMillis = Duration.between(started, Instant.now()).toMillis();
        if (Boolean.TRUE.equals(proposed.isError())) {
            log.warn(
                    "propose_purchase answered an error after {} ms: {}",
                    proposeMillis,
                    proposed.structuredContent());
            return;
        }
        Map<?, ?> decision = (Map<?, ?>) proposed.structuredContent();
        log.info(
                "propose_purchase: decision {} outcome {} reasons {} in {} ms",
                decision.get("decisionId"),
                decision.get("outcome"),
                decision.get("reasons"),
                proposeMillis);
        pollUntilSettled((String) decision.get("decisionId"));
    }

    private void pollUntilSettled(String decisionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_FOR);
        while (Instant.now().isBefore(deadline)) {
            McpSchema.CallToolResult read =
                    control.callTool(
                            McpSchema.CallToolRequest.builder("get_decision")
                                    .arguments(Map.of("decisionId", decisionId))
                                    .build());
            Map<?, ?> view = (Map<?, ?>) read.structuredContent();
            Object checkout = view == null ? null : view.get("checkout");
            log.info("get_decision: decision {} checkout {}", decisionId, checkout);
            if (checkout != null && SETTLED.contains(checkout.toString())) {
                return;
            }
            Thread.sleep(Duration.ofSeconds(1));
        }
        log.warn("decision {} did not settle within {}", decisionId, POLL_FOR);
    }
}
