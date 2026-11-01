package dev.agenticwallet.spike.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The OpenAI-compatible chat client wired against a local stub: proves the wiring without a key.
 * The live hello against OpenRouter is {@link ChatHelloLiveTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.ai.mcp.client.enabled=false",
            "spring.ai.openai.api-key=synthetic-key"
        })
class ChatHelloStubTest {

    private static final String REPLY =
            """
            {"id":"chatcmpl-synthetic","object":"chat.completion","created":1,
             "model":"synthetic/model","choices":[{"index":0,"finish_reason":"stop",
             "message":{"role":"assistant","content":"hello"}}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static final HttpServer STUB;
    private static final List<String> REQUESTED_PATHS = new CopyOnWriteArrayList<>();

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the chat stub", e);
        }
        // Answers any path and records it, so the test can state which path the client really
        // calls.
        STUB.createContext(
                "/",
                exchange -> {
                    REQUESTED_PATHS.add(exchange.getRequestURI().getPath());
                    byte[] body = REPLY.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        STUB.start();
    }

    @DynamicPropertySource
    static void stubUrl(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.ai.openai.base-url",
                () ->
                        "http://"
                                + STUB.getAddress().getAddress().getHostAddress()
                                + ":"
                                + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Autowired ChatHello hello;

    /**
     * Finding (T-008): Spring AI 2.0's OpenAI model is built on the official OpenAI Java SDK, which
     * appends {@code /chat/completions} to the base URL. The base URL must therefore end in {@code
     * /v1} ({@code https://openrouter.ai/api/v1}); Spring AI 1.x appended {@code /v1} itself.
     */
    @Test
    void saysHelloThroughTheOpenAiCompatibleClient() {
        assertThat(hello.hello()).isEqualTo("hello");
        System.out.println(
                "T-008 finding: base-url ending in /api/v1 produced request path "
                        + REQUESTED_PATHS);
        assertThat(REQUESTED_PATHS).hasSize(1);
        assertThat(REQUESTED_PATHS.getFirst()).endsWith("/chat/completions");
    }
}
