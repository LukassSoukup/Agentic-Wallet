package dev.agenticwallet.spike.backend.mandates;

import dev.agenticwallet.spike.core.decisioning.MandateStatus;
import dev.agenticwallet.spike.core.decisioning.MandateTerms;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.events.MandateRevoked;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published API of {@code mandates}: the terms a decision is made under, the completed purchases
 * (kept by the listener on {@code CheckoutCompleted}), and revocation, which the outbox announces.
 */
@Component
public class Mandates {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    Mandates(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public Optional<MandateTerms> termsOf(MandateId id) {
        return jdbc.sql(
                        "select id, version, status, limit_per_purchase, budget, currency from"
                                + " mandates.mandate where id = :id")
                .param("id", id.value())
                .query((row, n) ->
                        new MandateTerms(
                                new MandateId(row.getObject("id", java.util.UUID.class)),
                                row.getInt("version"),
                                MandateStatus.valueOf(row.getString("status")),
                                new Money(
                                        row.getBigDecimal("limit_per_purchase"),
                                        java.util.Currency.getInstance(row.getString("currency"))),
                                new Money(
                                        row.getBigDecimal("budget"),
                                        java.util.Currency.getInstance(row.getString("currency")))))
                .optional();
    }

    public int completedPurchasesOf(MandateId id) {
        return jdbc.sql("select count(*) from mandates.completed_purchase where mandate_id = :id")
                .param("id", id.value())
                .query(Integer.class)
                .single();
    }

    @Transactional
    public void revoke(MandateId id) {
        jdbc.sql("update mandates.mandate set status = 'REVOKED' where id = :id")
                .param("id", id.value())
                .update();
        events.publishEvent(new MandateRevoked(id.value()));
    }
}
