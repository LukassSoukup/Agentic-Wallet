package dev.agenticwallet.spike.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for the backend's agent API: records the Authorization header it receives and answers
 * a fixed SYNTHETIC decision, or a problem when the header is a token it does not know.
 */
final class BackendStub implements AutoCloseable {

    static final String KNOWN_TOKEN = "Bearer synthetic-agent-token-0001";
    static final String DECISION_ID = "00000000-0000-0000-0000-00000000d001";

    private final HttpServer server;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    BackendStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/agent/proposals", this::propose);
        server.createContext("/api/v1/agent/decisions/", this::decision);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    List<String> authorizationsSeen() {
        return List.copyOf(authorizations);
    }

    private void propose(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(String.valueOf(authorization));
        if (!KNOWN_TOKEN.equals(authorization)) {
            answer(
                    exchange,
                    401,
                    "application/problem+json",
                    "{\"type\":\"urn:agentic-wallet:control-api:unknown-agent-session\",\"title\":\"Unauthorized\",\"status\":401}");
            return;
        }
        answer(
                exchange,
                200,
                "application/json",
                "{\"decisionId\":\""
                        + DECISION_ID
                        + "\",\"outcome\":\"APPROVE\",\"reasons\":[\"WITHIN_LIMIT\"],\"checkout\":null}");
    }

    private void decision(HttpExchange exchange) throws IOException {
        authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        answer(
                exchange,
                200,
                "application/json",
                "{\"decisionId\":\""
                        + DECISION_ID
                        + "\",\"outcome\":\"APPROVE\",\"reasons\":[\"WITHIN_LIMIT\"],\"checkout\":\"COMPLETED\"}");
    }

    private static void answer(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
