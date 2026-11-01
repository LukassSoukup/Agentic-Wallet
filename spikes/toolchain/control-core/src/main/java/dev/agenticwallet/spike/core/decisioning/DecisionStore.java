package dev.agenticwallet.spike.core.decisioning;

import java.util.Optional;
import java.util.UUID;

/**
 * Port: the append-only store of decisions.
 *
 * <p>Inputs: a complete {@link DecisionRecord}. Failure modes: {@code append} throws a named
 * exception when the id already exists (a repeated delivery must be caught before this point).
 * Idempotency: {@code find} is a read; {@code append} is not idempotent and callers guarantee one
 * call per decision.
 */
public interface DecisionStore {
    void append(DecisionRecord record);

    Optional<DecisionRecord> find(UUID id);
}
