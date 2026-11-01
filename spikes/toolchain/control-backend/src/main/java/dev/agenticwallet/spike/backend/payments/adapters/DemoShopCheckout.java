package dev.agenticwallet.spike.backend.payments.adapters;

import dev.agenticwallet.spike.backend.payments.PaymentsProperties;
import dev.agenticwallet.spike.core.payments.Checkout;
import dev.agenticwallet.spike.core.payments.Credential;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link Checkout} adapter for the demo shop's session API ("gateway bypassed", 05-open-items).
 * Translates every transport and HTTP failure into the port's modes (ADR-0009 B3); never retries a
 * completion (B6). Two clients, because the completion call has its own read timeout.
 */
@Component
class DemoShopCheckout implements Checkout {

    private final RestClient sessions;
    private final RestClient completions;

    DemoShopCheckout(PaymentsProperties properties, RestClient.Builder builder) {
        this.sessions = client(builder, properties, properties.connectTimeout());
        this.completions = client(builder, properties, properties.completionTimeout());
    }

    private static RestClient client(
            RestClient.Builder builder,
            PaymentsProperties properties,
            java.time.Duration readTimeout) {
        var settings =
                HttpClientSettings.defaults()
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(properties.demoShopUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .build();
    }

    record LineBody(String merchantItemId, int quantity) {}

    record OpenBody(String merchantReference, List<LineBody> lines) {}

    record SessionBody(
            String sessionId, String state, BigDecimal total, String currency, Instant expiresAt) {}

    record CompleteBody(String credential, String idempotencyKey) {}

    record CompletionBody(String sessionId, String state, String authorisationId) {}

    @Override
    public OpenResult openSession(MerchantReference merchant, List<OrderLine> lines) {
        OpenBody body =
                new OpenBody(
                        merchant.value(),
                        lines.stream()
                                .map(line ->
                                        new LineBody(
                                                line.merchantItemId().value(), line.quantity()))
                                .toList());
        try {
            SessionBody session =
                    sessions.post().uri("/sessions").body(body).retrieve().body(SessionBody.class);
            if (session == null) {
                return new OpenResult.Unavailable("merchant-empty-answer");
            }
            return new OpenResult.Opened(
                    session.sessionId(),
                    new Money(session.total(), Currency.getInstance(session.currency())),
                    session.expiresAt());
        } catch (RestClientResponseException refused) {
            return new OpenResult.Unavailable(
                    refused.getStatusCode().is4xxClientError()
                            ? "merchant-refused-session"
                            : "merchant-error");
        } catch (RestClientException unreachable) {
            return new OpenResult.Unavailable("merchant-unreachable");
        }
    }

    @Override
    public CompletionResult complete(
            String sessionId, Credential credential, String idempotencyKey) {
        try {
            CompletionBody completion =
                    completions
                            .post()
                            .uri("/sessions/{id}/complete", sessionId)
                            .body(new CompleteBody(credential.value(), idempotencyKey))
                            .retrieve()
                            .body(CompletionBody.class);
            if (completion == null) {
                return new CompletionResult.NoAnswer();
            }
            return new CompletionResult.Completed(completion.authorisationId());
        } catch (RestClientResponseException answered) {
            // a 5xx says nothing about whether the merchant completed: UNKNOWN, never FAILED
            if (!answered.getStatusCode().is4xxClientError()) {
                return new CompletionResult.NoAnswer();
            }
            return new CompletionResult.Refused(
                    answered.getStatusCode() == HttpStatus.CONFLICT
                            ? "session-already-completed"
                            : "merchant-refused-completion");
        } catch (RestClientException noAnswer) {
            return new CompletionResult.NoAnswer();
        }
    }

    @Override
    public SessionStatus status(String sessionId) {
        try {
            SessionBody session =
                    sessions.get()
                            .uri("/sessions/{id}", sessionId)
                            .retrieve()
                            .body(SessionBody.class);
            if (session == null) {
                return new SessionStatus.NoAnswer();
            }
            return "COMPLETED".equals(session.state())
                    ? new SessionStatus.Completed(sessionId)
                    : new SessionStatus.Open();
        } catch (RestClientResponseException answered) {
            return answered.getStatusCode() == HttpStatus.NOT_FOUND
                    ? new SessionStatus.NotFound()
                    : new SessionStatus.NoAnswer();
        } catch (RestClientException noAnswer) {
            return new SessionStatus.NoAnswer();
        }
    }
}
