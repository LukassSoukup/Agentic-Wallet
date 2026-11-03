package dev.agenticwallet.spike.kernel.ids;

import java.util.UUID;

/** Identity of a mandate; the version is a separate fact. */
public record MandateId(UUID value) {

    public MandateId {
        if (value == null) {
            throw new IllegalArgumentException("MandateId requires a value");
        }
    }

    public static MandateId parse(String value) {
        try {
            return new MandateId(UUID.fromString(IdFormat.require("MandateId", value)));
        } catch (IllegalArgumentException notAUuid) {
            throw new InvalidIdFormatException("MandateId");
        }
    }
}
