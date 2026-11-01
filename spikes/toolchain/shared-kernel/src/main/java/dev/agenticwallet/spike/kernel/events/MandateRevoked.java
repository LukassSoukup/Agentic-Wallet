package dev.agenticwallet.spike.kernel.events;

import java.util.UUID;

/** The cardholder revoked a mandate; attempts still in {@code CREATED} are cancelled (ADR-0006). */
public record MandateRevoked(UUID mandateId, int schemaVersion) {
    public static final int SCHEMA_VERSION = 1;

    public MandateRevoked(UUID mandateId) {
        this(mandateId, SCHEMA_VERSION);
    }
}
