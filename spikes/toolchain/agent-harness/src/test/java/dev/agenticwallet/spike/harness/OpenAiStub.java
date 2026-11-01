package dev.agenticwallet.spike.harness;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local OpenAI-compatible stub: answers every path with one canned reply and records the paths.
 */
final class OpenAiStub {

    private static final String REPLY =
            """
            {"id":"chatcmpl-synthetic","object":"chat.completion","created":1,
             "model":"synthetic/model","choices":[{"index":0,"finish_reason":"stop",
             "message":{"role":"assistant","content":"hello"}}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private final HttpServer server;
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

    OpenAiStub() {
        try {
            server =
                    HttpServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the chat stub", e);
        }
        server.createContext(
                "/",
                exchange -> {
                    requestedPaths.add(exchange.getRequestURI().getPath());
                    byte[] body = REPLY.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.start();
    }

    /** The stub's origin, scheme, host and port only. */
    String origin() {
        return "http://"
                + server.getAddress().getAddress().getHostAddress()
                + ":"
                + server.getAddress().getPort();
    }

    List<String> requestedPaths() {
        return List.copyOf(requestedPaths);
    }

    void stop() {
        server.stop(0);
    }
}
