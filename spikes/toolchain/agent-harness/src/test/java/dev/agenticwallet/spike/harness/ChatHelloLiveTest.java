package dev.agenticwallet.spike.harness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The real hello against OpenRouter. Runs only when {@code OPENROUTER_API_KEY} is in the
 * environment (export it from the gitignored .env); skipped otherwise, so CI without the secret
 * stays green.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.ai.mcp.client.enabled=false")
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class ChatHelloLiveTest {

    @Autowired ChatHello hello;

    @Test
    void openRouterAnswers() {
        assertThat(hello.hello()).containsIgnoringCase("hello");
    }
}
