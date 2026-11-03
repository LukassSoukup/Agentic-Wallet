package dev.agenticwallet.spike.mcp.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * One process, two MCP servers, two listeners: the gateway's discovery tool answers on the gateway
 * port only, the control server's tools on the main port only, and neither lists the other's tools.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"skeleton.gateway.port=18784", "skeleton.mcp.backend-url=http://localhost:1"})
class HostedConnectorGatewayTest {

    @LocalServerPort int controlPort;

    @Test
    void gatewayToolsLiveOnTheGatewayListenerOnly() {
        try (McpSyncClient gateway = connect(18784, HostedConnectorGateway.ENDPOINT)) {
            McpSchema.ListToolsResult tools = gateway.listTools();
            assertThat(tools.tools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactly(HostedConnectorGateway.DISCOVERY_TOOL);
            McpSchema.CallToolResult result =
                    gateway.callTool(
                            McpSchema.CallToolRequest.builder(HostedConnectorGateway.DISCOVERY_TOOL)
                                    .arguments(Map.of("query", "kettle"))
                                    .build());
            assertThat(String.valueOf(result.structuredContent())).contains("SYN-0001");
        }
        try (McpSyncClient control = connect(controlPort, "/mcp")) {
            assertThat(control.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("propose_purchase", "get_decision")
                    .doesNotContain(HostedConnectorGateway.DISCOVERY_TOOL);
        }
        assertThatThrownBy(() -> connect(controlPort, HostedConnectorGateway.ENDPOINT).close())
                .isInstanceOf(RuntimeException.class);
    }

    private static McpSyncClient connect(int port, String endpoint) {
        var transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint(endpoint)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
        McpSyncClient client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        client.initialize();
        return client;
    }
}
