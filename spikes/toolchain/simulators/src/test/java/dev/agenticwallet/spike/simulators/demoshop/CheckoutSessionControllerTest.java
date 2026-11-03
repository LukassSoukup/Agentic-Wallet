package dev.agenticwallet.spike.simulators.demoshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import dev.agenticwallet.spike.simulators.processor.HookResponderClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The SYNTHETIC demo shop: a priced session, completion idempotent on the caller's key, and exactly
 * one authorisation at the processor per completed session.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "skeleton.processor.hook-responder-url=http://localhost:1")
@AutoConfigureTestRestTemplate
class CheckoutSessionControllerTest {

    @Autowired TestRestTemplate http;
    @MockitoBean HookResponderClient hook;

    @Test
    void opensAPricedSessionCompletesItOnceAndRaisesOneAuthorisation() {
        Mockito.when(hook.ask(any()))
                .thenReturn(new HookResponderClient.HookAnswer("MATCH", "pa-1", "x"));
        Map<String, Object> open =
                Map.of(
                        "merchantReference",
                        "demo-shop",
                        "lines",
                        java.util.List.of(Map.of("merchantItemId", "SYN-0001", "quantity", 2)));

        ResponseEntity<Map<String, Object>> opened = post("/sessions", open);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(opened.getBody())
                .containsEntry("total", 59.8)
                .containsEntry("currency", "EUR")
                .containsEntry("synthetic", true);
        String sessionId = (String) opened.getBody().get("sessionId");

        Map<String, Object> completion =
                Map.of("credential", "syn-cred", "idempotencyKey", "key-1");
        ResponseEntity<Map<String, Object>> first =
                post("/sessions/" + sessionId + "/complete", completion);
        ResponseEntity<Map<String, Object>> again =
                post("/sessions/" + sessionId + "/complete", completion);
        ResponseEntity<Map<String, Object>> otherKey =
                post(
                        "/sessions/" + sessionId + "/complete",
                        Map.of("credential", "syn-cred", "idempotencyKey", "key-2"));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody())
                .containsEntry("authorisationId", first.getBody().get("authorisationId"));
        assertThat(otherKey.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(hook, Mockito.times(1)).ask(any());
        assertThat(get("/sessions/" + sessionId)).containsEntry("state", "COMPLETED");
    }

    @Test
    void refusesAnUnknownItemAndAMissingCredential() {
        ResponseEntity<Map<String, Object>> unknown =
                post(
                        "/sessions",
                        Map.of(
                                "merchantReference",
                                "demo-shop",
                                "lines",
                                java.util.List.of(
                                        Map.of("merchantItemId", "NOPE", "quantity", 1))));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> opened =
                post(
                        "/sessions",
                        Map.of(
                                "merchantReference",
                                "demo-shop",
                                "lines",
                                java.util.List.of(
                                        Map.of("merchantItemId", "SYN-0002", "quantity", 1))));
        ResponseEntity<Map<String, Object>> noCredential =
                post(
                        "/sessions/" + opened.getBody().get("sessionId") + "/complete",
                        Map.of("credential", "", "idempotencyKey", "k"));
        assertThat(noCredential.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("unchecked") // TestRestTemplate cannot carry the generic Map type
    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return (ResponseEntity<Map<String, Object>>)
                (ResponseEntity<?>) http.postForEntity(path, body, Map.class);
    }

    @SuppressWarnings("unchecked") // TestRestTemplate cannot carry the generic Map type
    private Map<String, Object> get(String path) {
        return (Map<String, Object>) http.getForObject(path, Map.class);
    }
}
