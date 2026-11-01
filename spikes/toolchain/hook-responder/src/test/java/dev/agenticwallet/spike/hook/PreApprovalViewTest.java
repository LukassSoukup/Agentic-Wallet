package dev.agenticwallet.spike.hook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.core.decisioning.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** JdbcClient against PostgreSQL 18 in Testcontainers; the table is the one the backend owns. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PreApprovalViewTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired JdbcClient jdbc;
    @Autowired PreApprovalView view;

    @Test
    void readsAnOutcomeWithPlainSql() {
        UUID id = UUID.randomUUID();
        jdbc.sql("create schema if not exists decisioning").update();
        jdbc.sql(
                        "create table if not exists decisioning.decision (id uuid primary key,"
                                + " outcome varchar(16) not null)")
                .update();
        jdbc.sql("insert into decisioning.decision (id, outcome) values (:id, 'APPROVE')")
                .param("id", id)
                .update();

        assertThat(view.outcomeOf(id)).contains(Outcome.APPROVE);
        assertThat(view.outcomeOf(UUID.randomUUID())).isEmpty();
    }
}
