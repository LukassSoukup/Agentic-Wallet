package dev.agenticwallet.spike.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.agenticwallet.spike.backend.decisioning.DecisionService;
import dev.agenticwallet.spike.backend.support.TestPostgres;
import dev.agenticwallet.spike.kernel.Money;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.CompletedEventPublications;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The outbox end to end: the decision's transaction writes the decision and the publication row;
 * after commit the audit listener runs in its own transaction and the publication completes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxTest {

    @ServiceConnection static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @Autowired DecisionService decisions;
    @Autowired JdbcClient jdbc;
    @Autowired CompletedEventPublications completed;

    @Test
    void decisionReachesTheAuditModuleThroughTheOutbox() {
        UUID id = UUID.randomUUID();

        decisions.decide(id, Money.of("10.00", "EUR"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    Integer rows =
                            jdbc.sql(
                                            "select count(*) from audit.audit_event where"
                                                    + " decision_id = :id")
                                    .param("id", id)
                                    .query(Integer.class)
                                    .single();
                    assertThat(rows).isEqualTo(1);
                });
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    String status =
                            jdbc.sql(
                                            "select status from event_publication where"
                                                    + " serialized_event like :id")
                                    .param("id", "%" + id + "%")
                                    .query(String.class)
                                    .single();
                    assertThat(status).isEqualTo("COMPLETED");
                });
    }
}
