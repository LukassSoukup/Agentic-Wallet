package dev.agenticwallet.spike.kernel.ids;

import java.util.regex.Pattern;

/**
 * The fixed format of an id that comes from the untrusted side (ADR-0009 B4): at most 64 characters
 * from {@code [A-Za-z0-9_-]}, which every UUID string also satisfies. Parse, don't validate: the id
 * types call this once in their factory and are valid ever after.
 */
final class IdFormat {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private IdFormat() {}

    static String require(String idType, String value) {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new InvalidIdFormatException(idType);
        }
        return value;
    }
}
