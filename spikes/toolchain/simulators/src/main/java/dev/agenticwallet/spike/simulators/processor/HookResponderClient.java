package dev.agenticwallet.spike.simulators.processor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The processor's call to our hook responder, SYNTHETIC on the processor side: one POST per
 * authorisation with the processor's own authorisation id, explicit timeouts (ADR-0009 B6).
 */
@Component
public class HookResponderClient {

    private final RestClient client;

    HookResponderClient(ProcessorProperties properties, RestClient.Builder builder) {
        var settings =
                HttpClientSettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(properties.hookTimeout());
        this.client =
                builder.baseUrl(properties.hookResponderUrl())
                        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                        .build();
    }

    public record HookRequest(
            String processorAuthorisationId,
            BigDecimal amount,
            String currency,
            String merchantReference,
            Instant occurredAt) {}

    public record HookAnswer(
            String answer, String preApprovalId, String processorAuthorisationId) {}

    public HookAnswer ask(HookRequest request) {
        HookAnswer answer =
                client.post()
                        .uri("/hook/authorisations")
                        .body(request)
                        .retrieve()
                        .body(HookAnswer.class);
        if (answer == null) {
            throw new EmptyHookAnswerException(request.processorAuthorisationId());
        }
        return answer;
    }

    static final class EmptyHookAnswerException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        EmptyHookAnswerException(String processorAuthorisationId) {
            super("the hook responder returned no body for " + processorAuthorisationId);
        }
    }
}
