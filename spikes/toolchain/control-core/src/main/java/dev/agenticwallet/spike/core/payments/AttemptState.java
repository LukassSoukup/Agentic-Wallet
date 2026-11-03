package dev.agenticwallet.spike.core.payments;

import java.util.EnumSet;
import java.util.Set;

/**
 * The checkout attempt's states and the transitions 06-consistency §3 allows (the skeleton leaves
 * out {@code PENDING_USER}). Every transition in the backend is a compare-and-set on the state it
 * expects, so this table is the whole rule; the adapters add no judgement.
 */
public enum AttemptState {
    CREATED,
    CREDENTIAL_REQUESTED,
    COMPLETING,
    COMPLETED,
    FAILED,
    UNKNOWN,
    CANCELLED;

    public boolean canMoveTo(AttemptState next) {
        return nextStates().contains(next);
    }

    public boolean isTerminal() {
        return nextStates().isEmpty();
    }

    private Set<AttemptState> nextStates() {
        return switch (this) {
            case CREATED -> EnumSet.of(CREDENTIAL_REQUESTED, CANCELLED);
            case CREDENTIAL_REQUESTED -> EnumSet.of(COMPLETING, FAILED, UNKNOWN);
            case COMPLETING -> EnumSet.of(COMPLETED, FAILED, UNKNOWN);
            case UNKNOWN -> EnumSet.of(COMPLETED, FAILED);
            case COMPLETED, FAILED, CANCELLED -> EnumSet.noneOf(AttemptState.class);
        };
    }
}
