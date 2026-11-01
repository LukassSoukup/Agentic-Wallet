package dev.agenticwallet.spike.backend.skeleton;

import dev.agenticwallet.spike.backend.support.OffsetClock;
import dev.agenticwallet.spike.backend.support.TestPostgres;
import dev.agenticwallet.spike.backend.support.TestSimulators;
import dev.agenticwallet.spike.kernel.Clock;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One backend context for every scenario test: PostgreSQL 18 in Testcontainers, the simulators as a
 * process, short timeouts so the scenarios run in seconds, and the offset clock. The staleness
 * bound stays above the listener deadline the properties imply (3 × (0.5 s + 2 s) + 1 s + 1.5 s +
 * 0.5 s = 10.5 s), or the context would refuse to start.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.hikari.connection-timeout=500",
            "skeleton.database.transaction-timeout=2s",
            "spring.modulith.events.staleness.processing=PT12S",
            "spring.modulith.events.staleness.check-interval=PT1S",
            "skeleton.payments.connect-timeout=PT1S",
            "skeleton.payments.credential-timeout=PT1S",
            "skeleton.payments.completion-timeout=PT1.5S",
            "skeleton.payments.commit-bound=PT0.5S",
            "skeleton.payments.resolver-interval=PT1S",
            "skeleton.payments.session-lifetime=PT15M",
            "skeleton.decisioning.pre-approval-window=PT168H"
        })
@Import(SkeletonBackendTest.ScenarioConfiguration.class)
@AutoConfigureTestRestTemplate
public abstract class SkeletonBackendTest {

    /**
     * The synthetic agent session the tests act as; the token is test configuration, not a secret.
     */
    public static final String AGENT_TOKEN = "synthetic-agent-token-0001";

    @ServiceConnection static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @DynamicPropertySource
    static void simulatorsAndIdentity(DynamicPropertyRegistry registry) {
        registry.add("skeleton.payments.demo-shop-url", () -> TestSimulators.URL);
        registry.add("skeleton.payments.network-url", () -> TestSimulators.URL);
        registry.add("skeleton.identity.sessions[0].token", () -> AGENT_TOKEN);
        registry.add(
                "skeleton.identity.sessions[0].cardholder-id",
                () -> "00000000-0000-0000-0000-00000000c001");
        registry.add(
                "skeleton.identity.sessions[0].agent-instance-id",
                () -> "00000000-0000-0000-0000-00000000b001");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScenarioConfiguration {
        /** One primary {@link Clock}; it is also injectable as {@link OffsetClock}. */
        @Bean
        @Primary
        OffsetClock clock() {
            return new OffsetClock();
        }

        @Bean
        RecordedCheckoutEvents recordedCheckoutEvents() {
            return new RecordedCheckoutEvents();
        }

        @Bean
        SkeletonFixtures fixtures(JdbcClient jdbc, OffsetClock clock, RestClient.Builder builder) {
            return new SkeletonFixtures(jdbc, clock, builder, TestSimulators.URL);
        }
    }
}
