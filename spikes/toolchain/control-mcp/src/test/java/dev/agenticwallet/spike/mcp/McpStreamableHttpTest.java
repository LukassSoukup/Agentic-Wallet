package dev.agenticwallet.spike.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Checklist (b): the MCP SDK over streamable HTTP. A client with default settings advertises no
 * sampling, elicitation or roots; tool list and call work; {@code notifications/tools/list_changed}
 * reaches a client that keeps the GET stream open.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpStreamableHttpTest {

    @LocalServerPort int port;
    @Autowired McpSyncServer server;

    private McpSyncClient client;

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    private McpSyncClient connect(
            boolean openGetStream, AtomicReference<List<McpSchema.Tool>> changes) {
        var transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .openConnectionOnStartup(openGetStream)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
        McpClient.SyncSpec spec =
                McpClient.sync(transport)
                        .requestTimeout(Duration.ofSeconds(10))
                        .clientInfo(
                                McpSchema.Implementation.builder("t008-spike-client", "0.0.1")
                                        .build());
        if (changes != null) {
            spec = spec.toolsChangeConsumer(changes::set);
        }
        McpSyncClient built = spec.build();
        built.initialize();
        return built;
    }

    @Test
    void listsAndCallsTheToolAndSeesNoClientCapabilities() {
        client = connect(false, null);

        McpSchema.ListToolsResult tools = client.listTools();
        assertThat(tools.tools())
                .extracting(McpSchema.Tool::name)
                .contains(AgentConfigurationTool.NAME);
        assertThat(client.getServerCapabilities().tools().listChanged()).isTrue();
        assertThat(client.getServerCapabilities().prompts()).isNull();
        assertThat(client.getServerCapabilities().resources()).isNull();

        McpSchema.CallToolResult result =
                client.callTool(
                        McpSchema.CallToolRequest.builder(AgentConfigurationTool.NAME)
                                .arguments(Map.of())
                                .build());

        assertThat(result.isError()).isNotEqualTo(true);
        @SuppressWarnings(
                "unchecked") // structuredContent() is Object; the tool returns a JSON object
        Map<String, Object> body = (Map<String, Object>) result.structuredContent();
        assertThat(body)
                .containsEntry("clientAdvertisedRoots", false)
                .containsEntry("clientAdvertisedSampling", false)
                .containsEntry("clientAdvertisedElicitation", false)
                .containsEntry("clientName", "t008-spike-client");
        assertThat(client.getClientCapabilities().roots()).isNull();
        assertThat(client.getClientCapabilities().sampling()).isNull();
        assertThat(client.getClientCapabilities().elicitation()).isNull();
    }

    @Test
    void toolsListChangedReachesAClientWithAnOpenGetStream() {
        AtomicReference<List<McpSchema.Tool>> changes = new AtomicReference<>();
        client = connect(true, changes);
        String extraTool = "spike_extra_tool_" + port;

        server.addTool(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(
                                McpSchema.Tool.builder(
                                                extraTool,
                                                Map.of("type", "object", "properties", Map.of()))
                                        .description("added at runtime")
                                        .build())
                        .callHandler((exchange, request) ->
                                McpSchema.CallToolResult.builder().addTextContent("ok").build())
                        .build());
        try {
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() ->
                            assertThat(changes.get())
                                    .isNotNull()
                                    .extracting(McpSchema.Tool::name)
                                    .contains(extraTool));
        } finally {
            server.removeTool(extraTool);
        }
    }
}
