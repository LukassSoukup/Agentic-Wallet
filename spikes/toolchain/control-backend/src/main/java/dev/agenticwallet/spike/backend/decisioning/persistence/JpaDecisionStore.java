package dev.agenticwallet.spike.backend.decisioning.persistence;

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
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Currency;
import java.util.Optional;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
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
        if (repository.existsById(record.id().value())) {
            throw new DuplicateDecisionException(record.id());
        }
        try {
            entityManager.persist(toEntity(record));
            entityManager.flush();
        } catch (ConstraintViolationException raced) {
            // two appends of the same id in flight at once: the unique key decides and the loser
            // lands here; any other persistence failure propagates as what it is
            throw new DuplicateDecisionException(record.id(), raced);
        }
    }

    @Override
    public Optional<DecisionRecord> find(DecisionId id) {
        return repository.findById(id.value()).map(JpaDecisionStore::toRecord);
    }

    private static DecisionEntity toEntity(DecisionRecord record) {
        return new DecisionEntity(
                record.id().value(),
                record.decidedAt(),
                record.mandateId().value(),
                record.merchant().value(),
                record.checkoutSessionId(),
                record.amount().amount(),
                record.amount().currency().getCurrencyCode(),
                record.verdict().outcome().name(),
                record.verdict().reasons().stream()
                        .map(ReasonCode::name)
                        .collect(Collectors.joining(",")));
    }

    private static DecisionRecord toRecord(DecisionEntity entity) {
        return new DecisionRecord(
                new DecisionId(entity.id()),
                entity.decidedAt(),
                new MandateId(entity.mandateId()),
                MerchantReference.parse(entity.merchantReference()),
                entity.checkoutSessionId(),
                new Money(entity.amount(), Currency.getInstance(entity.currency())),
                new Verdict(
                        Outcome.valueOf(entity.outcome()),
                        Arrays.stream(entity.reasons().split(","))
                                .map(ReasonCode::valueOf)
                                .toList()));
    }
}
