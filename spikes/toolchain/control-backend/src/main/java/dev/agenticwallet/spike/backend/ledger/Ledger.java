package dev.agenticwallet.spike.backend.ledger;

import dev.agenticwallet.spike.core.decisioning.LedgerSnapshot;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Currency;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published API of {@code ledger} (06-consistency §1). Every method runs inside the caller's
 * transaction ({@code MANDATORY}): the decision's for lock and reserve, the attempt's for count and
 * release, so the three facts of §3 change atomically. {@code decisioning} never touches these
 * tables.
 */
@Component
public class Ledger {

    private final JdbcClient jdbc;
    private final Clock clock;

    Ledger(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Creates the period row if it is the first decision of the period, then locks it {@code FOR
     * UPDATE} and reads the totals. The caller holds the lock until its transaction ends.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerSnapshot lockAndRead(MandateId mandate, LocalDate periodStart, Money budget) {
        jdbc.sql(
                        "insert into ledger.budget_period (mandate_id, period_start, currency,"
                                + " budget) values (:mandate, :period, :currency, :budget) on"
                                + " conflict do nothing")
                .param("mandate", mandate.value())
                .param("period", periodStart)
                .param("currency", budget.currency().getCurrencyCode())
                .param("budget", budget.amount())
                .update();
        return jdbc.sql(
                        "select reserved, counted, currency from ledger.budget_period where"
                                + " mandate_id = :mandate and period_start = :period for update")
                .param("mandate", mandate.value())
                .param("period", periodStart)
                .query((row, n) -> {
                    Currency currency = Currency.getInstance(row.getString("currency"));
                    return new LedgerSnapshot(
                            new Money(row.getBigDecimal("reserved"), currency),
                            new Money(row.getBigDecimal("counted"), currency));
                })
                .single();
    }

    /** Only after {@link #lockAndRead} in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(
            DecisionId decision, MandateId mandate, LocalDate periodStart, Money amount) {
        jdbc.sql(
                        "insert into ledger.reservation (decision_id, mandate_id, period_start,"
                            + " amount, currency, state, changed_at) values (:decision, :mandate,"
                            + " :period, :amount, :currency, 'RESERVED', :at)")
                .param("decision", decision.value())
                .param("mandate", mandate.value())
                .param("period", periodStart)
                .param("amount", amount.amount())
                .param("currency", amount.currency().getCurrencyCode())
                .param("at", Timestamp.from(clock.now()))
                .update();
        jdbc.sql(
                        "update ledger.budget_period set reserved = reserved + :amount where"
                                + " mandate_id = :mandate and period_start = :period")
                .param("amount", amount.amount())
                .param("mandate", mandate.value())
                .param("period", periodStart)
                .update();
    }

    /** RESERVED becomes COUNTED at the same amount; a second call changes nothing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void count(DecisionId decision) {
        move(decision, "COUNTED", "reserved = reserved - r.amount, counted = counted + r.amount");
    }

    /** RESERVED is released on FAILED or CANCELLED; a second call changes nothing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(DecisionId decision) {
        move(decision, "RELEASED", "reserved = reserved - r.amount");
    }

    /**
     * The reservation row is the compare-and-set: only the caller that moves it out of RESERVED
     * (one row updated) touches the period totals, so two concurrent calls cannot both add.
     */
    private void move(DecisionId decision, String toState, String periodUpdate) {
        int moved =
                jdbc.sql(
                                "update ledger.reservation set state = :state, changed_at = :at"
                                        + " where decision_id = :decision and state = 'RESERVED'")
                        .param("state", toState)
                        .param("at", Timestamp.from(clock.now()))
                        .param("decision", decision.value())
                        .update();
        if (moved == 1) {
            jdbc.sql(
                            "update ledger.budget_period p set "
                                    + periodUpdate
                                    + " from ledger.reservation r where r.decision_id = :decision"
                                    + " and p.mandate_id = r.mandate_id and p.period_start ="
                                    + " r.period_start")
                    .param("decision", decision.value())
                    .update();
        }
    }
}
