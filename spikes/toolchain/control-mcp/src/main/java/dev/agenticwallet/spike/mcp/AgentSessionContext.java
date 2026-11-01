package dev.agenticwallet.spike.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Carries the harness's bearer token from the HTTP request into the tool handler through the SDK's
 * transport context, so {@code control-mcp} can forward it verbatim (ADR-0006) without ever parsing
 * it: the backend is the only verifier (B1).
 */
final class AgentSessionContext implements McpTransportContextExtractor<ServerRequest> {

    static final String AUTHORIZATION = "authorization";

    @Override
    public McpTransportContext extract(ServerRequest request) {
        String header = request.headers().firstHeader("Authorization");
        return header == null
                ? McpTransportContext.EMPTY
                : McpTransportContext.create(Map.of(AUTHORIZATION, header));
    }

    static Optional<String> bearerFrom(McpTransportContext context) {
        Object header = context.get(AUTHORIZATION);
        return header instanceof String value && value.startsWith("Bearer ")
                ? Optional.of(value)
                : Optional.empty();
    }
}
