package dev.agenticwallet.spike.kernel.ids;

import java.util.UUID;

/** Identity of one decision; a step-up outcome is a second decision with its own id. */
public record DecisionId(UUID value) {

    public DecisionId {
        if (value == null) {
            throw new IllegalArgumentException("DecisionId requires a value");
        }
    }

    public static DecisionId parse(String value) {
        try {
            return new DecisionId(UUID.fromString(IdFormat.require("DecisionId", value)));
        } catch (IllegalArgumentException notAUuid) {
            throw new InvalidIdFormatException("DecisionId");
        }
    }
}
