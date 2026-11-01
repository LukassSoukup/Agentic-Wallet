package dev.agenticwallet.spike.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 06-consistency §4 against PostgreSQL 18: one pre-approval matches one authorisation; a second id
 * against the same pre-approval does not; the same id twice gets the stored answer; one cent over
 * and a cancelled pre-approval do not match. SYNTHETIC data, shadow mode.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "skeleton.hook.mode=SHADOW")
@Sql(scripts = "classpath:published-view.sql")
@AutoConfigureTestRestTemplate
class HookResponderTest {

    @ServiceConnection static final PostgreSQLContainer POSTGRES = HookTestPostgres.INSTANCE;

    private final Instant now = Instant.now();

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @Test
    void onePreApprovalMatchesExactlyOneAuthorisationAndRepeatsItsAnswer() {
        UUID preApproval = publish("80.00", "demo-shop", "ACTIVE");

        Map<String, Object> first = ask("auth-" + UUID.randomUUID(), "80.00", "demo-shop");
        Map<String, Object> second = ask("auth-" + UUID.randomUUID(), "79.00", "demo-shop");
        Map<String, Object> replay =
                ask((String) first.get("processorAuthorisationId"), "80.00", "demo-shop");

        assertThat(first)
                .containsEntry("answer", "MATCH")
                .containsEntry("preApprovalId", preApproval.toString())
                .containsEntry("mode", "SHADOW");
        assertThat(second).containsEntry("answer", "NO_MATCH");
        assertThat(replay)
                .containsEntry("answer", "MATCH")
                .containsEntry("preApprovalId", preApproval.toString());
        assertThat(callLogRows((String) first.get("processorAuthorisationId"))).isEqualTo(1);
    }

    @Test
    void oneCentOverAnotherMerchantOrACancelledPreApprovalDoesNotMatch() {
        publish("50.00", "other-shop", "ACTIVE");
        publish("50.00", "cancelled-shop", "CANCELLED");

        assertThat(ask("auth-" + UUID.randomUUID(), "50.01", "other-shop"))
                .containsEntry("answer", "NO_MATCH");
        assertThat(ask("auth-" + UUID.randomUUID(), "50.00", "cancelled-shop"))
                .containsEntry("answer", "NO_MATCH");
        assertThat(ask("auth-" + UUID.randomUUID(), "50.00", "other-shop"))
                .containsEntry("answer", "MATCH");
    }

    @Test
    void theSameProcessorIdDeliveredTwiceAtOnceGetsOneAnswerAndOneCallLogRow() throws Exception {
        UUID preApproval = publish("40.00", "twin-shop", "ACTIVE");
        String processorId = "twin-" + UUID.randomUUID();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(() -> {
                        start.await();
                        return ask(processorId, "40.00", "twin-shop");
                    });
            var second =
                    pool.submit(() -> {
                        start.await();
                        return ask(processorId, "40.00", "twin-shop");
                    });
            start.countDown();
            assertThat(first.get())
                    .containsEntry("answer", "MATCH")
                    .containsEntry("preApprovalId", preApproval.toString());
            assertThat(second.get())
                    .containsEntry("answer", "MATCH")
                    .containsEntry("preApprovalId", preApproval.toString());
        }
        assertThat(callLogRows(processorId)).isEqualTo(1);
    }

    @Test
    void consumesTheSmallestPreApprovalThatFits() {
        UUID large = publish("89.00", "two-shop", "ACTIVE");
        UUID small = publish("29.90", "two-shop", "ACTIVE");

        assertThat(ask("auth-" + UUID.randomUUID(), "29.90", "two-shop"))
                .containsEntry("preApprovalId", small.toString());
        assertThat(ask("auth-" + UUID.randomUUID(), "89.00", "two-shop"))
                .containsEntry("preApprovalId", large.toString());
    }

    @Test
    void refusesAProcessorIdOutsideTheFixedFormat() {
        ResponseEntity<Map<String, Object>> answer =
                postRaw(
                        Map.of(
                                "processorAuthorisationId",
                                "bad id; drop table",
                                "amount",
                                "1.00",
                                "currency",
                                "EUR",
                                "merchantReference",
                                "demo-shop",
                                "occurredAt",
                                now.toString()));
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.getBody().toString()).doesNotContain("drop table");
    }

    private UUID publish(String amount, String merchant, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "insert into authorisation.pre_approval (decision_id, amount, currency,"
                            + " merchant_reference, valid_from, valid_until, state) values (:id,"
                            + " :amount, 'EUR', :merchant, :from, :until, :state)")
                .param("id", id)
                .param("amount", new BigDecimal(amount))
                .param("merchant", merchant)
                .param("from", Timestamp.from(now.minusSeconds(60)))
                .param("until", Timestamp.from(now.plusSeconds(3600)))
                .param("state", state)
                .update();
        return id;
    }

    private Map<String, Object> ask(String processorId, String amount, String merchant) {
        return postRaw(
                        Map.of(
                                "processorAuthorisationId",
                                processorId,
                                "amount",
                                amount,
                                "currency",
                                "EUR",
                                "merchantReference",
                                merchant,
                                "occurredAt",
                                now.toString()))
                .getBody();
    }

    @SuppressWarnings("unchecked") // TestRestTemplate cannot carry the generic Map type
    private ResponseEntity<Map<String, Object>> postRaw(Map<String, Object> body) {
        return (ResponseEntity<Map<String, Object>>)
                (ResponseEntity<?>) http.postForEntity("/hook/authorisations", body, Map.class);
    }

    private int callLogRows(String processorId) {
        return jdbc.sql("select count(*) from hook.call_log where processor_authorisation_id = :id")
                .param("id", processorId)
                .query(Integer.class)
                .single();
    }
}
