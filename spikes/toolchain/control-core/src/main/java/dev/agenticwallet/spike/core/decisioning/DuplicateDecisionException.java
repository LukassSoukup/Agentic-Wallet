package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.ids.DecisionId;

/** A decision id was appended twice; the store is append-only and refuses the second write. */
public final class DuplicateDecisionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public DuplicateDecisionException(DecisionId id) {
        super("decision " + id.value() + " already exists");
    }

    public DuplicateDecisionException(DecisionId id, Throwable cause) {
        super("decision " + id.value() + " already exists", cause);
    }
}
