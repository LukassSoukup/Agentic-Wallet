package dev.agenticwallet.spike.backend.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.backend.support.TestPostgres;
import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Checklist (d): Flyway migrates a real PostgreSQL 18 in Testcontainers on aarch64, Hibernate
 * validates the mapping against the result (the context would not start otherwise), and
 * numeric(19,2) round-trips a BigDecimal unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DecisionPersistenceTest {

    @ServiceConnection static final PostgreSQLContainer POSTGRES = TestPostgres.INSTANCE;

    @Autowired DecisionStore store;
    @Autowired JdbcClient jdbc;

    @Test
    void flywayAppliedEveryMigration() {
        Integer applied =
                jdbc.sql("select count(*) from flyway_schema_history where success")
                        .query(Integer.class)
                        .single();
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void amountColumnIsExactDecimal() {
        Map<String, Object> column =
                jdbc.sql(
                                "select data_type, numeric_precision, numeric_scale from"
                                        + " information_schema.columns where table_schema ="
                                        + " 'decisioning' and table_name = 'decision' and"
                                        + " column_name = 'amount'")
                        .query()
                        .singleRow();
        assertThat(column)
                .containsEntry("data_type", "numeric")
                .containsEntry("numeric_precision", 19)
                .containsEntry("numeric_scale", 2);
    }

    @Test
    void roundTripsADecisionThroughJpa() {
        UUID id = UUID.randomUUID();
        DecisionRecord record =
                new DecisionRecord(
                        id,
                        Instant.parse("2026-11-01T10:15:30Z"),
                        Money.of("99.99", "EUR"),
                        new Verdict(Outcome.APPROVE, List.of(ReasonCode.WITHIN_LIMIT)));

        store.append(record);

        assertThat(store.find(id)).contains(record);
    }
}
