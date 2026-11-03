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
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "skeleton.payments.demo-shop-url=http://localhost:1",
            "skeleton.payments.network-url=http://localhost:1",
            // a short staleness bound for the hang below, above the listener deadline these
            // timeouts imply
            "spring.datasource.hikari.connection-timeout=500",
            "skeleton.database.transaction-timeout=1s",
            "skeleton.payments.credential-timeout=PT0.5S",
            "skeleton.payments.completion-timeout=PT0.5S",
            "skeleton.payments.commit-bound=PT0.2S",
            "spring.modulith.events.staleness.processing=PT6S",
            "spring.modulith.events.staleness.check-interval=PT1S",
            "skeleton.identity.sessions[0].token=synthetic-test-token",
            "skeleton.identity.sessions[0].cardholder-id=00000000-0000-0000-0000-00000000c001",
            "skeleton.identity.sessions[0].agent-instance-id=00000000-0000-0000-0000-00000000b001"
        })
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
        tx.executeWithoutResult(status -> events.publishEvent(new HangEvent(id, 12_000)));

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("PROCESSING"));
        // staleness.processing is PT6S with a PT1S check interval (the properties above)
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(statusOf(id)).isEqualTo("FAILED"));
        assertThat(listeners.hangDeliveries()).isEqualTo(1);

        // What the registry records once the hung listener finally returns is a characterisation,
        // not a guarantee: T-008 (PT3S bound, 8 s hang) saw the late return overwrite FAILED with
        // COMPLETED; T-037 (PT6S bound, 12 s hang) saw FAILED stay. The design relies on neither:
        // B6's compare-and-set on the attempt is the fence, and the resubmission job must tolerate
        // both. The assertion records that the outcome is one of the two and nothing else.
        await().atMost(Duration.ofSeconds(16)).until(() -> listeners.hangReturns() == 1);
        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(statusOf(id)).isIn("FAILED", "COMPLETED"));
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
