package dev.agenticwallet.spike.kernel;

/**
 * Text from an agent or a merchant (invariant 3): data that is stored and shown, never read by a
 * rule and never logged. {@code toString} prints the type and the length only (ADR-0009 B4).
 */
public record UntrustedText(String content) {

    public UntrustedText {
        if (content == null) {
            throw new IllegalArgumentException(
                    "UntrustedText requires content; use an empty string");
        }
    }

    public static UntrustedText of(String content) {
        return new UntrustedText(content);
    }

    @Override
    public String toString() {
        return "UntrustedText[length=" + content.length() + "]";
    }
}
