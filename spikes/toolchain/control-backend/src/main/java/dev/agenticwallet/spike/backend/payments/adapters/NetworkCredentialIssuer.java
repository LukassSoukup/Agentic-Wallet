package dev.agenticwallet.spike.backend.payments.adapters;

import dev.agenticwallet.spike.backend.payments.PaymentsProperties;
import dev.agenticwallet.spike.core.payments.Credential;
import dev.agenticwallet.spike.core.payments.CredentialIssuer;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** {@link CredentialIssuer} adapter for the network simulator; explicit timeouts, no retry. */
@Component
class NetworkCredentialIssuer implements CredentialIssuer {

    private final RestClient client;

    NetworkCredentialIssuer(PaymentsProperties properties, RestClient.Builder builder) {
        var settings =
                HttpClientSettings.defaults()
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.credentialTimeout());
        this.client =
                builder.clone()
                        .baseUrl(properties.networkUrl())
                        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                        .build();
    }

    record CredentialRequestBody(UUID decisionId, BigDecimal amount, String currency) {}

    record CredentialBody(String credential) {}

    @Override
    public CredentialResult request(DecisionId decisionId, Money amount) {
        try {
            CredentialBody body =
                    client.post()
                            .uri("/network/credentials")
                            .body(
                                    new CredentialRequestBody(
                                            decisionId.value(),
                                            amount.amount(),
                                            amount.currency().getCurrencyCode()))
                            .retrieve()
                            .body(CredentialBody.class);
            if (body == null) {
                return new CredentialResult.NoAnswer();
            }
            return new CredentialResult.Issued(new Credential(body.credential()));
        } catch (RestClientResponseException answered) {
            return answered.getStatusCode().is4xxClientError()
                    ? new CredentialResult.Refused("network-refused-credential")
                    : new CredentialResult.NoAnswer();
        } catch (RestClientException noAnswer) {
            return new CredentialResult.NoAnswer();
        }
    }
}
