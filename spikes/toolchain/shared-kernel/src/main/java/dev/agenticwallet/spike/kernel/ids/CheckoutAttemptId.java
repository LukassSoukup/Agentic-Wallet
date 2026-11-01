package dev.agenticwallet.spike.kernel.ids;

import java.util.UUID;

/** Identity of the one checkout attempt an approved decision has. */
public record CheckoutAttemptId(UUID value) {

    public CheckoutAttemptId {
        if (value == null) {
            throw new IllegalArgumentException("CheckoutAttemptId requires a value");
        }
    }

    public static CheckoutAttemptId parse(String value) {
        try {
            return new CheckoutAttemptId(
                    UUID.fromString(IdFormat.require("CheckoutAttemptId", value)));
        } catch (IllegalArgumentException notAUuid) {
            throw new InvalidIdFormatException("CheckoutAttemptId");
        }
    }
}
