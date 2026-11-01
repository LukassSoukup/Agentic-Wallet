package dev.agenticwallet.spike.kernel.events;

import java.util.UUID;

/** The checkout attempt of an approved decision reached {@code COMPLETED} (06-consistency §3). */
public record CheckoutCompleted(
        UUID decisionId, UUID attemptId, UUID mandateId, int schemaVersion) {
    public static final int SCHEMA_VERSION = 1;

    public CheckoutCompleted(UUID decisionId, UUID attemptId, UUID mandateId) {
        this(decisionId, attemptId, mandateId, SCHEMA_VERSION);
    }
}
