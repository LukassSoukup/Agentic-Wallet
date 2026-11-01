package dev.agenticwallet.spike.core.payments;

/**
 * A single-use network credential. It lives in memory between the request and the completion call
 * and is never stored or logged: {@code toString} prints the type name only (ADR-0009 B2).
 */
public record Credential(String value) {

    public Credential {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a credential has a value");
        }
    }

    @Override
    public String toString() {
        return "Credential";
    }
}
