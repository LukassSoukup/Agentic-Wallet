package dev.agenticwallet.spike.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.agenticwallet.spike.backend.support.TestPostgres;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR-0009 §A Spring Modulith: a listener that throws leaves its publication FAILED; our own job
 * resubmits failed publications only, through the registry's failed-publications API; the staleness
 * monitor turns a listener lost mid-flight (here: one that hangs past the bound) into a failed
 * publication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(FailedPublicationResubmissionTest.SyntheticListeners.class)
class FailedPublicationResubmissionTest {

    @ServiceConnection static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    /** Synthetic test events; labelled as such, never in production code. */
    record FailOnceEvent(UUID id) {}

    record HangEvent(UUID id, long hangMillis) {}

    /**
     * The listener bean is proxied for {@code @Async} and {@code @Transactional}, so a field read
     * through the injected reference sees the proxy's uninitialised fields; counts go through
     * methods, which the proxy delegates.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticListeners {

        private final AtomicInteger failOnceDeliveries = new AtomicInteger();
        private final AtomicInteger hangDeliveries = new AtomicInteger();
        private final AtomicInteger hangReturns = new AtomicInteger();

        int failOnceDeliveries() {
            return failOnceDeliveries.get();
        }

        int hangDeliveries() {
            return hangDeliveries.get();
        }

        int hangReturns() {
            return hangReturns.get();
        }

        @ApplicationModuleListener
        void on(FailOnceEvent event) {
            if (failOnceDeliveries.incrementAndGet() == 1) {
                throw new SyntheticListenerFailure(event.id());
            }
        }

        @ApplicationModuleListener
        void on(HangEvent event) throws InterruptedException {
            hangDeliveries.incrementAndGet();
            Thread.sleep(event.hangMillis());
            hangReturns.incrementAndGet();
        }
    }

    static final class SyntheticListenerFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SyntheticListenerFailure(UUID id) {
            super("synthetic failure for " + id);
        }
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcClient jdbc;
    @Autowired FailedEventPublications failed;
    @Autowired SyntheticListeners listeners;

    @Test
    void failedPublicationIsResubmittedByTheFailedPublicationsApiOnly() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(status -> events.publishEvent(new FailOnceEvent(id)));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("FAILED"));
        assertThat(listeners.failOnceDeliveries()).isEqualTo(1);

        failed.resubmit(ResubmissionOptions.defaults().withMinAge(Duration.ZERO));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("COMPLETED"));
        assertThat(listeners.failOnceDeliveries()).isEqualTo(2);
        assertThat(completionAttemptsOf(id)).isEqualTo(2);
    }

    @Test
    void stalenessMonitorMarksAHungListenerFailed() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(status -> events.publishEvent(new HangEvent(id, 8_000)));

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("PROCESSING"));
        // staleness.processing is PT3S with a PT1S check interval (application.yaml)
        await().atMost(Duration.ofSeconds(8))
                .untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("FAILED"));
        assertThat(listeners.hangDeliveries()).isEqualTo(1);

        // What the registry records once the hung listener finally returns is part of the finding.
        await().atMost(Duration.ofSeconds(12)).until(() -> listeners.hangReturns() == 1);
        // Characterises Spring Modulith 2.1.2: the late return overwrites the monitor's FAILED with
        // COMPLETED. The monitor bounds a hang; it does not fence it. B6's compare-and-set on the
        // attempt is the fence. If an upgrade changes this, the finding in the report changes too.
        assertThat(statusOf(id)).isEqualTo("COMPLETED");
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from event_publication where serialized_event like :id")
                .param("id", "%" + id + "%")
                .query(String.class)
                .single();
    }

    private int completionAttemptsOf(UUID id) {
        return jdbc.sql(
                        "select completion_attempts from event_publication where serialized_event"
                                + " like :id")
                .param("id", "%" + id + "%")
                .query(Integer.class)
                .single();
    }
}
