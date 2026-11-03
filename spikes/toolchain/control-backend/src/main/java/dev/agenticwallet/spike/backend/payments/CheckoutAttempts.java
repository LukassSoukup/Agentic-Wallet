package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.CheckoutAttemptId;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The attempt rows. Every transition is one {@code UPDATE … WHERE state = expected}: under READ
 * COMMITTED a concurrent updater re-checks the condition after the row lock, so exactly one of two
 * racing transitions wins and the other changes nothing (06-consistency §3, ADR-0009 B6).
 */
@Component
class CheckoutAttempts {

    private static final Logger log = LoggerFactory.getLogger(CheckoutAttempts.class);

    private static final RowMapper<CheckoutAttempt> ROW =
            (row, n) -> {
                Timestamp deadline = row.getTimestamp("call_deadline");
                return new CheckoutAttempt(
                        new CheckoutAttemptId(row.getObject("id", UUID.class)),
                        new DecisionId(row.getObject("decision_id", UUID.class)),
                        new MandateId(row.getObject("mandate_id", UUID.class)),
                        AttemptState.valueOf(row.getString("state")),
                        row.getString("checkout_session_id"),
                        row.getTimestamp("session_expires_at").toInstant(),
                        MerchantReference.parse(row.getString("merchant_reference")),
                        new Money(
                                row.getBigDecimal("amount"),
                                Currency.getInstance(row.getString("currency"))),
                        Optional.ofNullable(deadline).map(Timestamp::toInstant));
            };

    private static final String COLUMNS =
            "id, decision_id, mandate_id, state, checkout_session_id, session_expires_at,"
                    + " merchant_reference, amount, currency, call_deadline";

    private final JdbcClient jdbc;
    private final Clock clock;

    CheckoutAttempts(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Idempotent on the decision id: a redelivered event finds the row that exists. */
    @Transactional(propagation = Propagation.MANDATORY)
    void createIfAbsent(
            DecisionId decision,
            MandateId mandate,
            String sessionId,
            Instant sessionExpiresAt,
            MerchantReference merchant,
            Money amount) {
        jdbc.sql(
                        "insert into payments.checkout_attempt (decision_id, id, mandate_id, state,"
                                + " checkout_session_id, session_expires_at, merchant_reference,"
                                + " amount, currency, last_transition_at) values (:decision, :id,"
                                + " :mandate, 'CREATED', :session, :expires, :merchant, :amount,"
                                + " :currency, :now) on conflict (decision_id) do nothing")
                .param("decision", decision.value())
                .param("id", UUID.randomUUID())
                .param("mandate", mandate.value())
                .param("session", sessionId)
                .param("expires", Timestamp.from(sessionExpiresAt))
                .param("merchant", merchant.value())
                .param("amount", amount.amount())
                .param("currency", amount.currency().getCurrencyCode())
                .param("now", Timestamp.from(clock.now()))
                .update();
    }

    Optional<CheckoutAttempt> find(DecisionId decision) {
        return jdbc.sql(
                        "select "
                                + COLUMNS
                                + " from payments.checkout_attempt where decision_id = :d")
                .param("d", decision.value())
                .query(ROW)
                .optional();
    }

    List<CheckoutAttempt> inState(AttemptState state, MandateId mandate) {
        return jdbc.sql(
                        "select "
                                + COLUMNS
                                + " from payments.checkout_attempt where state = :s and mandate_id"
                                + " = :m")
                .param("s", state.name())
                .param("m", mandate.value())
                .query(ROW)
                .list();
    }

    /** Attempts whose announced call is past its deadline, plus every UNKNOWN one. */
    List<CheckoutAttempt> awaitingResolution(Instant now) {
        return jdbc.sql(
                        "select "
                                + COLUMNS
                                + " from payments.checkout_attempt where state = 'UNKNOWN' or"
                                + " (state in ('CREDENTIAL_REQUESTED', 'COMPLETING') and"
                                + " call_deadline < :now) order by last_transition_at")
                .param("now", Timestamp.from(now))
                .query(ROW)
                .list();
    }

    /**
     * The compare-and-set. Returns whether this caller made the transition; {@code false} means
     * another transition got there first and this caller must change nothing else.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean move(
            DecisionId decision,
            AttemptState from,
            AttemptState to,
            Optional<Instant> callDeadline,
            Optional<String> failureReason) {
        if (!from.canMoveTo(to)) {
            throw new IllegalTransitionException(from, to);
        }
        int rows =
                jdbc.sql(
                                "update payments.checkout_attempt set state = :to, call_deadline ="
                                    + " :deadline, failure_reason = :reason, last_transition_at ="
                                    + " :now where decision_id = :decision and state = :from")
                        .param("to", to.name())
                        .param("deadline", callDeadline.map(Timestamp::from).orElse(null))
                        .param("reason", failureReason.orElse(null))
                        .param("now", Timestamp.from(clock.now()))
                        .param("decision", decision.value())
                        .param("from", from.name())
                        .update();
        if (rows == 1) {
            // ids and codes only (B4): the state transition is the fact an operator needs
            log.info(
                    "attempt for decision {} moved {} -> {}{}",
                    decision.value(),
                    from,
                    to,
                    failureReason.map(reason -> " (" + reason + ")").orElse(""));
        }
        return rows == 1;
    }

    static final class IllegalTransitionException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        IllegalTransitionException(AttemptState from, AttemptState to) {
            super("a checkout attempt cannot move from " + from + " to " + to);
        }
    }
}
