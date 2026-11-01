package dev.agenticwallet.spike.backend.decisioning.persistence;

import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.Money;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Persistence adapter for the {@link DecisionStore} port: maps records to entities and back. */
@Component
class JpaDecisionStore implements DecisionStore {

    private static final int AMOUNT_SCALE = 2;

    private final DecisionJpaRepository repository;

    JpaDecisionStore(DecisionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(DecisionRecord record) {
        BigDecimal amount = record.amount().amount();
        if (amount.scale() > AMOUNT_SCALE) {
            throw new ScaleExceedsColumnException("decision.amount", amount, AMOUNT_SCALE);
        }
        repository.save(
                new DecisionEntity(
                        record.id(),
                        record.decidedAt(),
                        amount,
                        record.amount().currency().getCurrencyCode(),
                        record.verdict().outcome().name(),
                        record.verdict().reasons().stream()
                                .map(ReasonCode::name)
                                .collect(Collectors.joining(","))));
    }

    @Override
    public Optional<DecisionRecord> find(UUID id) {
        return repository.findById(id).map(JpaDecisionStore::toRecord);
    }

    private static DecisionRecord toRecord(DecisionEntity entity) {
        return new DecisionRecord(
                entity.id(),
                entity.decidedAt(),
                new Money(entity.amount(), Currency.getInstance(entity.currency())),
                new Verdict(
                        Outcome.valueOf(entity.outcome()),
                        Arrays.stream(entity.reasons().split(","))
                                .map(ReasonCode::valueOf)
                                .toList()));
    }
}
