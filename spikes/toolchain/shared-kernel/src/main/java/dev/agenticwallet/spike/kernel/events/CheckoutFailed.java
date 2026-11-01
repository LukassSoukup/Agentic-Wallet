package dev.agenticwallet.spike.kernel.events;

import java.util.UUID;

/** The checkout attempt ended in {@code FAILED}; the reason is a code, never merchant text. */
public record CheckoutFailed(
        UUID decisionId, UUID attemptId, String reasonCode, int schemaVersion) {
    public static final int SCHEMA_VERSION = 1;

    public CheckoutFailed(UUID decisionId, UUID attemptId, String reasonCode) {
        this(decisionId, attemptId, reasonCode, SCHEMA_VERSION);
    }
}
