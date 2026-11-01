package dev.agenticwallet.spike.simulators.network;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.simulators.demoshop.DemoShopApplication;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The SYNTHETIC network counts credential requests per decision, so a scenario can read "exactly
 * one".
 */
@SpringBootTest(
        classes = DemoShopApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "skeleton.processor.hook-responder-url=http://localhost:1")
@AutoConfigureTestRestTemplate
class CredentialControllerTest {

    @Autowired TestRestTemplate http;

    @Test
    void issuesDistinctCredentialsAndCountsRequestsPerDecision() {
        UUID decision = UUID.randomUUID();
        Map<String, Object> request =
                Map.of("decisionId", decision, "amount", "80.00", "currency", "EUR");

        Map<String, Object> first = post("/network/credentials", request);
        Map<String, Object> second = post("/network/credentials", request);

        assertThat(first.get("credential")).isNotEqualTo(second.get("credential"));
        assertThat(get("/network/credentials/" + decision + "/requests"))
                .containsEntry("requests", 2);
        assertThat(get("/network/credentials/" + UUID.randomUUID() + "/requests"))
                .containsEntry("requests", 0);
    }

    @SuppressWarnings("unchecked") // TestRestTemplate cannot carry the generic Map type
    private Map<String, Object> post(String path, Object body) {
        return (Map<String, Object>) http.postForObject(path, body, Map.class);
    }

    @SuppressWarnings("unchecked") // TestRestTemplate cannot carry the generic Map type
    private Map<String, Object> get(String path) {
        return (Map<String, Object>) http.getForObject(path, Map.class);
    }
}
