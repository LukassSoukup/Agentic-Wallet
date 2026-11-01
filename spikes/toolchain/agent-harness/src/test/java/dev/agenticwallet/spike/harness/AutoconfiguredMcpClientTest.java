package dev.agenticwallet.spike.harness;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring AI's autoconfigured client, as the harness will use it, against an MCP server hosted in
 * the same test process: the server must see no roots, sampling or elicitation (ADR-0009 §A). The
 * port is fixed because the client's URL is bound before the server picks a port.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
            "server.port=18765",
            "spring.ai.openai.api-key=synthetic-key",
            "skeleton.agent-session-token=synthetic-agent-token-0001",
            "spring.ai.mcp.client.initialized=false",
            "spring.ai.mcp.client.streamable-http.connections.control.url=http://localhost:18765",
            "spring.ai.mcp.server.protocol=streamable",
            "spring.ai.mcp.server.capabilities.prompt=false",
            "spring.ai.mcp.server.capabilities.resource=false",
            "spring.ai.mcp.server.capabilities.completion=false"
        })
@Import(AutoconfiguredMcpClientTest.EchoTool.class)
class AutoconfiguredMcpClientTest {

    static final String TOOL = "echo_client_capabilities";

    @TestConfiguration(proxyBeanMethods = false)
    static class EchoTool {
        @Bean
        List<McpServerFeatures.SyncToolSpecification> echoTools() {
            return List.of(
                    McpServerFeatures.SyncToolSpecification.builder()
                            .tool(
                                    McpSchema.Tool.builder(
                                                    TOOL,
                                                    Map.of(
                                                            "type",
                                                            "object",
                                                            "properties",
                                                            Map.of()))
                                            .description(
                                                    "echoes the client's advertised capabilities")
                                            .build())
                            .callHandler((exchange, request) -> {
                                McpSchema.ClientCapabilities c = exchange.getClientCapabilities();
                                return McpSchema.CallToolResult.builder()
                                        .structuredContent(
                                                Map.of(
                                                        "roots", c.roots() != null,
                                                        "sampling", c.sampling() != null,
                                                        "elicitation", c.elicitation() != null,
                                                        "clientName",
                                                                exchange.getClientInfo().name()))
                                        .addTextContent("capabilities")
                                        .build();
                            })
                            .build());
        }
    }

    @Autowired List<McpSyncClient> clients;

    @Test
    void harnessClientAdvertisesNoRootsSamplingOrElicitation() {
        assertThat(clients).hasSize(1);
        McpSyncClient client = clients.getFirst();
        client.initialize();

        McpSchema.CallToolResult result =
                client.callTool(
                        McpSchema.CallToolRequest.builder(TOOL).arguments(Map.of()).build());

        @SuppressWarnings(
                "unchecked") // structuredContent() is Object; the tool returns a JSON object
        Map<String, Object> seen = (Map<String, Object>) result.structuredContent();
        assertThat(seen)
                .containsEntry("roots", false)
                .containsEntry("sampling", false)
                .containsEntry("elicitation", false)
                .containsEntry("clientName", "agent-harness");
    }
}
