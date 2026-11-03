package dev.agenticwallet.spike.harness;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADR-0006: the harness presents its agent session token on every MCP request as a bearer header;
 * {@code control-mcp} forwards it verbatim and the backend verifies it. The token is configuration
 * (B2) and is never logged; the type prints nothing of it.
 */
@Component
class AgentSessionHeader implements McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> {

    private final String token;

    AgentSessionHeader(@Value("${skeleton.agent-session-token}") String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("skeleton.agent-session-token is required");
        }
        this.token = token;
    }

    @Override
    public void customize(String name, HttpClientStreamableHttpTransport.Builder builder) {
        builder.addHttpRequestCustomizer((request, method, uri, body, context) ->
                request.header("Authorization", "Bearer " + token));
    }

    @Override
    public String toString() {
        return "AgentSessionHeader";
    }
}
