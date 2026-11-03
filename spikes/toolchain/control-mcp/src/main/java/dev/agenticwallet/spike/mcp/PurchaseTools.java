package dev.agenticwallet.spike.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two tools of the ADR-0006 surface: {@code propose_purchase} and {@code get_decision}. The agent's
 * identity is the bearer header the transport context carries, never a tool argument; a named
 * failure is a tool result flagged as an error with a typed body, never a stack trace (B3).
 */
@Configuration(proxyBeanMethods = false)
class PurchaseTools {

    static final String PROPOSE = "propose_purchase";
    static final String GET_DECISION = "get_decision";

    private final BackendClient backend;

    PurchaseTools(BackendClient backend) {
        this.backend = backend;
    }

    @Bean
    List<McpServerFeatures.SyncToolSpecification> purchaseToolSpecifications() {
        return List.of(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(
                                McpSchema.Tool.builder(PROPOSE, proposeSchema())
                                        .description(
                                                "Asks the control layer to approve one purchase at"
                                                    + " a merchant. Returns the decision with its"
                                                    + " reasons.")
                                        .build())
                        .callHandler(this::propose)
                        .build(),
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(
                                McpSchema.Tool.builder(
                                                GET_DECISION,
                                                Map.of(
                                                        "type", "object",
                                                        "properties",
                                                                Map.of(
                                                                        "decisionId",
                                                                        Map.of("type", "string")),
                                                        "required", List.of("decisionId")))
                                        .description(
                                                "Reads a decision and where its checkout stands.")
                                        .build())
                        .callHandler(this::decision)
                        .build());
    }

    private McpSchema.CallToolResult propose(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Optional<String> bearer = AgentSessionContext.bearerFrom(exchange.transportContext());
        if (bearer.isEmpty()) {
            return error(
                    "urn:agentic-wallet:control-mcp:no-agent-session",
                    "The agent session header is missing.");
        }
        Map<String, Object> arguments = request.arguments();
        @SuppressWarnings("unchecked") // the tool schema declares lines as an array of objects
        List<Map<String, Object>> lines = (List<Map<String, Object>>) arguments.get("lines");
        if (lines == null || lines.isEmpty()) {
            return error(
                    "urn:agentic-wallet:control-mcp:invalid-proposal",
                    "A proposal needs at least one line.");
        }
        BackendClient.ProposalBody body =
                new BackendClient.ProposalBody(
                        string(arguments, "purchaseKey"),
                        string(arguments, "mandateId"),
                        string(arguments, "merchantReference"),
                        lines.stream()
                                .map(line ->
                                        new BackendClient.LineBody(
                                                String.valueOf(line.get("merchantItemId")),
                                                ((Number) line.get("quantity")).intValue()))
                                .toList(),
                        string(arguments, "description"));
        return toResult(backend.propose(bearer.get(), body));
    }

    private McpSchema.CallToolResult decision(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Optional<String> bearer = AgentSessionContext.bearerFrom(exchange.transportContext());
        if (bearer.isEmpty()) {
            return error(
                    "urn:agentic-wallet:control-mcp:no-agent-session",
                    "The agent session header is missing.");
        }
        return toResult(backend.decision(bearer.get(), string(request.arguments(), "decisionId")));
    }

    private static McpSchema.CallToolResult toResult(BackendClient.Answer answer) {
        return switch (answer) {
            case BackendClient.Answer.Decision decision ->
                    McpSchema.CallToolResult.builder()
                            .structuredContent(decision.view())
                            .addTextContent("decision " + decision.view().get("outcome"))
                            .build();
            case BackendClient.Answer.Problem problem ->
                    error(problem.type().toString(), problem.title());
            case BackendClient.Answer.Unreachable ignored ->
                    error(
                            "urn:agentic-wallet:control-mcp:backend-unreachable",
                            "The control layer gave no answer.");
        };
    }

    private static McpSchema.CallToolResult error(String type, String title) {
        return McpSchema.CallToolResult.builder()
                .isError(true)
                .structuredContent(Map.of("type", type, "title", title))
                .addTextContent(title)
                .build();
    }

    private static String string(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> proposeSchema() {
        return Map.of(
                "type", "object",
                "properties",
                        Map.of(
                                "purchaseKey", Map.of("type", "string"),
                                "mandateId", Map.of("type", "string"),
                                "merchantReference", Map.of("type", "string"),
                                "lines",
                                        Map.of(
                                                "type",
                                                "array",
                                                "items",
                                                Map.of(
                                                        "type", "object",
                                                        "properties",
                                                                Map.of(
                                                                        "merchantItemId",
                                                                                Map.of(
                                                                                        "type",
                                                                                        "string"),
                                                                        "quantity",
                                                                                Map.of(
                                                                                        "type",
                                                                                        "integer")),
                                                        "required",
                                                                List.of(
                                                                        "merchantItemId",
                                                                        "quantity"))),
                                "description", Map.of("type", "string")),
                "required", List.of("purchaseKey", "mandateId", "merchantReference", "lines"));
    }
}
