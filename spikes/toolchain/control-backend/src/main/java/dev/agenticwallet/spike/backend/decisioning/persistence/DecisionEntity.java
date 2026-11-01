package dev.agenticwallet.spike.backend.decisioning.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Adapter-private JPA entity; never crosses the port. */
@Entity
@Table(name = "decision", schema = "decisioning")
class DecisionEntity {

    @Id private UUID id;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "reasons", nullable = false)
    private String reasons;

    protected DecisionEntity() {}

    DecisionEntity(
            UUID id,
            Instant decidedAt,
            BigDecimal amount,
            String currency,
            String outcome,
            String reasons) {
        this.id = id;
        this.decidedAt = decidedAt;
        this.amount = amount;
        this.currency = currency;
        this.outcome = outcome;
        this.reasons = reasons;
    }

    UUID id() {
        return id;
    }

    Instant decidedAt() {
        return decidedAt;
    }

    BigDecimal amount() {
        return amount;
    }

    String currency() {
        return currency;
    }

    String outcome() {
        return outcome;
    }

    String reasons() {
        return reasons;
    }
}
