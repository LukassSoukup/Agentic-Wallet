package dev.agenticwallet.spike.kernel.events;

import dev.agenticwallet.spike.kernel.Money;
import java.util.UUID;

/**
 * A decision was committed (ADR-0006). Consumed outside its publishing module, so it lives in the
 * kernel's events package: ids, amounts and enums only, as strings where the kernel knows no module
 * type. Fields are never renamed or removed (ADR-0009 B7); {@code schemaVersion} says which shape a
 * stored row has.
 */
public record DecisionMade(
        UUID decisionId,
        UUID mandateId,
        String outcome,
        Money amount,
        String merchantReference,
        String checkoutSessionId,
        int schemaVersion) {
    public static final int SCHEMA_VERSION = 2;

    public DecisionMade(
            UUID decisionId,
            UUID mandateId,
            String outcome,
            Money amount,
            String merchantReference,
            String checkoutSessionId) {
        this(
                decisionId,
                mandateId,
                outcome,
                amount,
                merchantReference,
                checkoutSessionId,
                SCHEMA_VERSION);
    }
}
