package dev.agenticwallet.spike.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * Checklist (e): Micrometer Tracing with the OpenTelemetry bridge; at the boundary a request with a
 * foreign {@code traceparent} gets a new trace id and the incoming trace as a span link.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BoundaryTraceTest.Exporter.class)
class BoundaryTraceTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Exporter {
        /** One bean: Boot's OTel autoconfiguration registers every {@code SpanExporter} bean. */
        @Bean
        InMemorySpanExporter spanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    private static final String INCOMING_TRACE = "0af7651916cd43dd8448eb211c80319c";
    private static final String INCOMING_SPAN = "b7ad6b7169203331";

    @LocalServerPort int port;
    @Autowired InMemorySpanExporter spans;

    @Test
    void startsANewTraceAndLinksTheIncomingOne() {
        RestClient.create("http://localhost:" + port)
                .get()
                .uri("/actuator/health")
                .header("traceparent", "00-" + INCOMING_TRACE + "-" + INCOMING_SPAN + "-01")
                .retrieve()
                .toBodilessEntity();

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(serverSpan()).isPresent());
        SpanData span = serverSpan().orElseThrow();

        assertThat(span.getTraceId()).isNotEqualTo(INCOMING_TRACE);
        assertThat(span.getParentSpanContext().isValid()).isFalse();
        assertThat(span.getLinks()).hasSize(1);
        assertThat(span.getLinks().getFirst().getSpanContext().getTraceId())
                .isEqualTo(INCOMING_TRACE);
        assertThat(span.getLinks().getFirst().getSpanContext().getSpanId())
                .isEqualTo(INCOMING_SPAN);
    }

    private Optional<SpanData> serverSpan() {
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
                .filter(s -> String.valueOf(s.getAttributes().asMap()).contains("/actuator/health"))
                .findFirst();
    }
}
