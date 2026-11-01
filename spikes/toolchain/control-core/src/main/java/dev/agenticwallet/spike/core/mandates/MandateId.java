package dev.agenticwallet.spike.core.mandates;

import java.util.UUID;

/** A typed id; exists so the ArchUnit slice rule has a second module package to check. */
public record MandateId(UUID value) {
    public MandateId {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
    }
}
