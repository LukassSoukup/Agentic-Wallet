package dev.agenticwallet.spike.kernel.events;

import java.util.UUID;

/**
 * Event consumed outside its publishing module, so it lives in the kernel's events package
 * (ADR-0006): ids and enums only, as strings here because the kernel knows no module type.
 */
public record DecisionMade(UUID decisionId, String outcome, int schemaVersion) {
    public static final int SCHEMA_VERSION = 1;

    public DecisionMade(UUID decisionId, String outcome) {
        this(decisionId, outcome, SCHEMA_VERSION);
    }
}
