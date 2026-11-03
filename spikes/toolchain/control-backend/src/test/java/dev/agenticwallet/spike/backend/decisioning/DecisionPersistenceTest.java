package dev.agenticwallet.spike.backend.decisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agenticwallet.spike.backend.support.TestPostgres;
import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.DuplicateDecisionException;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
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
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "skeleton.payments.demo-shop-url=http://localhost:1",
            "skeleton.payments.network-url=http://localhost:1",
            "skeleton.identity.sessions[0].token=synthetic-test-token",
            "skeleton.identity.sessions[0].cardholder-id=00000000-0000-0000-0000-00000000c001",
            "skeleton.identity.sessions[0].agent-instance-id=00000000-0000-0000-0000-00000000b001"
        })
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
        assertThat(applied).isEqualTo(9);
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
        DecisionId id = new DecisionId(UUID.randomUUID());
        DecisionRecord record =
                record(
                        id,
                        "2026-11-01T10:15:30Z",
                        "99.99",
                        Outcome.APPROVE,
                        ReasonCode.WITHIN_LIMIT);

        store.append(record);

        assertThat(store.find(id)).contains(record);
    }

    @Test
    void refusesToAppendTheSameIdTwiceAndKeepsTheFirstRecord() {
        DecisionId id = new DecisionId(UUID.randomUUID());
        DecisionRecord first =
                record(
                        id,
                        "2026-11-01T10:15:30Z",
                        "10.00",
                        Outcome.APPROVE,
                        ReasonCode.WITHIN_LIMIT);
        DecisionRecord second =
                record(
                        id,
                        "2026-11-01T10:16:00Z",
                        "999.00",
                        Outcome.DECLINE,
                        ReasonCode.OVER_LIMIT);
        store.append(first);

        assertThatThrownBy(() -> store.append(second))
                .isInstanceOf(DuplicateDecisionException.class);

        assertThat(store.find(id)).contains(first);
    }

    private static DecisionRecord record(
            DecisionId id, String at, String amount, Outcome outcome, ReasonCode reason) {
        return new DecisionRecord(
                id,
                Instant.parse(at),
                new MandateId(UUID.fromString("00000000-0000-0000-0000-00000000a001")),
                MerchantReference.parse("demo-shop"),
                "syn-session-test",
                Money.of(amount, "EUR"),
                new Verdict(outcome, List.of(reason)));
    }
}
