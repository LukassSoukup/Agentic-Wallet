package dev.agenticwallet.spike.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The MCP server starter and the MCP client starter coexist in one Boot process. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerAndClientInOneProcessTest {

    @LocalServerPort int port;

    @Test
    void servesADiscoveryToolOverStreamableHttp() {
        var transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build();
        try (McpSyncClient client =
                McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            McpSchema.CallToolResult result =
                    client.callTool(
                            McpSchema.CallToolRequest.builder(DiscoveryTool.NAME)
                                    .arguments(Map.of("query", "kettle"))
                                    .build());
            assertThat(result.isError()).isNotEqualTo(true);
            assertThat(String.valueOf(result.structuredContent())).contains("SYN-0001");
        }
    }
}
