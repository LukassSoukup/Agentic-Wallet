package dev.agenticwallet.spike.mcp;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The REST call to the backend's agent API (contract in {@code control-api}), with the caller's
 * bearer header forwarded verbatim and explicit timeouts. Every failure is a value the tool can
 * show the model as a typed fact (B3): the backend's problem, or a transport failure of ours.
 */
@Component
class BackendClient {

    private final RestClient client;

    BackendClient(McpProperties properties, RestClient.Builder builder) {
        var settings =
                HttpClientSettings.defaults()
                        .withConnectTimeout(properties.connectTimeout())
                        .withReadTimeout(properties.readTimeout());
        this.client =
                builder.baseUrl(properties.backendUrl())
                        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                        .build();
    }

    record LineBody(String merchantItemId, int quantity) {}

    record ProposalBody(
            String purchaseKey,
            String mandateId,
            String merchantReference,
            List<LineBody> lines,
            String description) {}

    sealed interface Answer {
        /** The backend's decision view, passed through as data. */
        record Decision(Map<String, Object> view) implements Answer {}

        /** The backend's RFC 9457 problem: its type is the typed fact the model sees. */
        record Problem(URI type, int status, String title) implements Answer {}

        /** No usable answer from the backend; never the transport's text. */
        record Unreachable() implements Answer {}
    }

    Answer propose(String authorization, ProposalBody proposal) {
        return exchange(() ->
                client.post()
                        .uri("/api/v1/agent/proposals")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .body(proposal)
                        .retrieve()
                        .body(MapType.MAP));
    }

    Answer decision(String authorization, String decisionId) {
        return exchange(() ->
                client.get()
                        .uri("/api/v1/agent/decisions/{id}", decisionId)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .retrieve()
                        .body(MapType.MAP));
    }

    private static Answer exchange(java.util.function.Supplier<Map<String, Object>> call) {
        try {
            Map<String, Object> view = call.get();
            return view == null ? new Answer.Unreachable() : new Answer.Decision(view);
        } catch (RestClientResponseException answered) {
            ProblemDetail problem = answered.getResponseBodyAs(ProblemDetail.class);
            URI type =
                    problem == null || problem.getType() == null
                            ? URI.create("about:blank")
                            : problem.getType();
            String title =
                    problem == null || problem.getTitle() == null
                            ? "unexpected-answer"
                            : problem.getTitle();
            return new Answer.Problem(type, answered.getStatusCode().value(), title);
        } catch (RestClientException unreachable) {
            return new Answer.Unreachable();
        }
    }

    /** The generic map type RestClient needs for a JSON object. */
    private static final class MapType {
        static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP =
                new org.springframework.core.ParameterizedTypeReference<>() {};

        private MapType() {}
    }
}
