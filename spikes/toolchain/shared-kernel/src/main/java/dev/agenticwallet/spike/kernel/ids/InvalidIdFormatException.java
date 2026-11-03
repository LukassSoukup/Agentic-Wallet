package dev.agenticwallet.spike.kernel.ids;

/** An id from the untrusted side did not have the fixed format ADR-0009 B4 requires. */
public final class InvalidIdFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    InvalidIdFormatException(String idType) {
        // the offending value is agent-supplied and never enters a message (B4)
        super(idType + " does not have the required format");
    }
}
