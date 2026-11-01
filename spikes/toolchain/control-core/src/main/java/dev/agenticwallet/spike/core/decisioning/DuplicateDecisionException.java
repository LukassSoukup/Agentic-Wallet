package dev.agenticwallet.spike.core.decisioning;

import java.util.UUID;

/** A decision id was appended twice; the store is append-only and refuses the second write. */
public final class DuplicateDecisionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public DuplicateDecisionException(UUID id) {
        super("decision " + id + " already exists");
    }

    public DuplicateDecisionException(UUID id, Throwable cause) {
        super("decision " + id + " already exists", cause);
    }
}
