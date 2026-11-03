package dev.agenticwallet.spike.kernel.events;

import java.util.UUID;

/**
 * The checkout attempt entered {@code UNKNOWN}: a timeout is never a failure (06-consistency §3).
 */
public record CheckoutOutcomeUnknown(UUID decisionId, UUID attemptId, int schemaVersion) {
    public static final int SCHEMA_VERSION = 1;

    public CheckoutOutcomeUnknown(UUID decisionId, UUID attemptId) {
        this(decisionId, attemptId, SCHEMA_VERSION);
    }
}
