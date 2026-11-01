package dev.agenticwallet.spike.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One tool of the ADR-0006 surface, built on the MCP SDK's own specification type so the spike
 * tests the SDK and not only Spring AI's annotation sugar. It echoes what the client advertised,
 * which is how the tests prove a client advertises no sampling, elicitation or roots.
 */
@Configuration(proxyBeanMethods = false)
class AgentConfigurationTool {

    static final String NAME = "get_agent_configuration";

    @Bean
    List<McpServerFeatures.SyncToolSpecification> agentConfigurationTools() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder(NAME, Map.of("type", "object", "properties", Map.of()))
                        .description(
                                "The agent's configuration: allowed models, spend cap and the"
                                        + " capabilities this client advertised.")
                        .build();
        return List.of(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler(AgentConfigurationTool::describe)
                        .build());
    }

    private static McpSchema.CallToolResult describe(
            McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        McpSchema.ClientCapabilities capabilities = exchange.getClientCapabilities();
        Map<String, Object> body =
                Map.of(
                        "allowedModels",
                        List.of("synthetic/model-a", "synthetic/model-b"),
                        "spendCapMinorUnits",
                        50_00,
                        "clientAdvertisedRoots",
                        capabilities.roots() != null,
                        "clientAdvertisedSampling",
                        capabilities.sampling() != null,
                        "clientAdvertisedElicitation",
                        capabilities.elicitation() != null,
                        "clientName",
                        exchange.getClientInfo().name());
        return McpSchema.CallToolResult.builder()
                .structuredContent(body)
                .addTextContent("agent configuration (synthetic spike data)")
                .build();
    }
}
