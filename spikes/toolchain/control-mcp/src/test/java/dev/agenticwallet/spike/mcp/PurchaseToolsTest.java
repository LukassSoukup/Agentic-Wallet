package dev.agenticwallet.spike.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The boundary: the harness's bearer header travels through the SDK's transport context into the
 * tool handler and on to the backend verbatim; a missing header or an unknown token is a typed
 * error result, never a transport failure (ADR-0006, ADR-0009 B1 and B3).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "skeleton.gateway.port=18783")
class PurchaseToolsTest {

    static BackendStub backend;

    @LocalServerPort int port;

    @BeforeAll
    static void startBackendStub() throws IOException {
        backend = new BackendStub();
    }

    @AfterAll
    static void stopBackendStub() {
        backend.close();
    }

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("skeleton.mcp.backend-url", () -> backend.url());
    }

    @Test
    void forwardsTheBearerHeaderAndReturnsTheDecisionAsStructuredContent() {
        try (McpSyncClient client = connect(BackendStub.KNOWN_TOKEN)) {
            McpSchema.CallToolResult result = client.callTool(proposeRequest());

            assertThat(result.isError()).isNotEqualTo(true);
            assertThat(String.valueOf(result.structuredContent()))
                    .contains(BackendStub.DECISION_ID)
                    .contains("APPROVE");
            assertThat(backend.authorizationsSeen()).contains(BackendStub.KNOWN_TOKEN);

            McpSchema.CallToolResult read =
                    client.callTool(
                            McpSchema.CallToolRequest.builder(PurchaseTools.GET_DECISION)
                                    .arguments(Map.of("decisionId", BackendStub.DECISION_ID))
                                    .build());
            assertThat(String.valueOf(read.structuredContent())).contains("COMPLETED");
        }
    }

    @Test
    void anUnknownTokenIsTheBackendsProblemTypeAndAMissingHeaderNeverReachesTheBackend() {
        try (McpSyncClient client = connect("Bearer synthetic-wrong-token")) {
            McpSchema.CallToolResult result = client.callTool(proposeRequest());
            assertThat(result.isError()).isTrue();
            assertThat(String.valueOf(result.structuredContent()))
                    .contains("unknown-agent-session");
        }
        int callsBefore = backend.authorizationsSeen().size();
        try (McpSyncClient client = connect(null)) {
            McpSchema.CallToolResult result = client.callTool(proposeRequest());
            assertThat(result.isError()).isTrue();
            assertThat(String.valueOf(result.structuredContent())).contains("no-agent-session");
        }
        assertThat(backend.authorizationsSeen()).hasSize(callsBefore);
    }

    private McpSyncClient connect(String authorization) {
        var transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .connectTimeout(Duration.ofSeconds(5));
        if (authorization != null) {
            transport.addHttpRequestCustomizer((request, method, uri, body, context) ->
                    request.header("Authorization", authorization));
        }
        McpSyncClient client =
                McpClient.sync(transport.build())
                        .requestTimeout(Duration.ofSeconds(10))
                        .clientInfo(
                                McpSchema.Implementation.builder("t037-driver", "0.0.1").build())
                        .build();
        client.initialize();
        return client;
    }

    private static McpSchema.CallToolRequest proposeRequest() {
        return McpSchema.CallToolRequest.builder(PurchaseTools.PROPOSE)
                .arguments(
                        Map.of(
                                "purchaseKey", "key-1",
                                "mandateId", "00000000-0000-0000-0000-00000000a001",
                                "merchantReference", "demo-shop",
                                "lines",
                                        List.of(
                                                Map.of(
                                                        "merchantItemId",
                                                        "SYN-0001",
                                                        "quantity",
                                                        1)),
                                "description", "synthetic"))
                .build();
    }
}
