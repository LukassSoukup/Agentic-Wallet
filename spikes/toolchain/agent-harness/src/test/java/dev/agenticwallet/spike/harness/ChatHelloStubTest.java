package dev.agenticwallet.spike.harness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The OpenAI-compatible chat client against a local stub: a base URL that carries OpenRouter's
 * {@code /api/v1} path is kept and {@code /chat/completions} appended, so the documented setting
 * works. {@link ChatHelloLiveTest} is the real call against OpenRouter.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.ai.mcp.client.enabled=false",
            "spring.ai.openai.api-key=synthetic-key"
        })
class ChatHelloStubTest {

    private static final OpenAiStub STUB = new OpenAiStub();

    @DynamicPropertySource
    static void stubUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> STUB.origin() + "/api/v1");
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @Autowired ChatHello hello;

    @Test
    void saysHelloAtTheBaseUrlPathPlusChatCompletions() {
        assertThat(hello.hello()).isEqualTo("hello");
        assertThat(STUB.requestedPaths()).containsExactly("/api/v1/chat/completions");
    }
}
