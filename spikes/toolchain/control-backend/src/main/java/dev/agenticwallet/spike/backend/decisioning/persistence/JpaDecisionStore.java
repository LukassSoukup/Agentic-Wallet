package dev.agenticwallet.spike.backend.decisioning.persistence;

import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.DuplicateDecisionException;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.Arrays;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for the {@link DecisionStore} port. Append-only: it persists, never merges,
 * so an id that already exists is refused as {@link DuplicateDecisionException} instead of being
 * overwritten (invariant 8).
 */
@Component
class JpaDecisionStore implements DecisionStore {

    private final EntityManager entityManager;
    private final DecisionJpaRepository repository;

    JpaDecisionStore(EntityManager entityManager, DecisionJpaRepository repository) {
        this.entityManager = entityManager;
        this.repository = repository;
    }

    @Override
    @Transactional
    public void append(DecisionRecord record) {
        if (repository.existsById(record.id())) {
            throw new DuplicateDecisionException(record.id());
        }
        try {
            entityManager.persist(toEntity(record));
            entityManager.flush();
        } catch (PersistenceException raced) {
            // two appends of the same id in flight at once: the unique key decides, and the loser
            // sees the constraint violation here
            throw new DuplicateDecisionException(record.id(), raced);
        }
    }

    @Override
    public Optional<DecisionRecord> find(UUID id) {
        return repository.findById(id).map(JpaDecisionStore::toRecord);
    }

    private static DecisionEntity toEntity(DecisionRecord record) {
        return new DecisionEntity(
                record.id(),
                record.decidedAt(),
                record.amount().amount(),
                record.amount().currency().getCurrencyCode(),
                record.verdict().outcome().name(),
                record.verdict().reasons().stream()
                        .map(ReasonCode::name)
                        .collect(Collectors.joining(",")));
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
