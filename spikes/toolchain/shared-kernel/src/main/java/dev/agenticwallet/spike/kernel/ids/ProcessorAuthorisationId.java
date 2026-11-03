package dev.agenticwallet.spike.kernel.ids;

/**
 * The processor's own id of one authorisation; it is logged, so it has the fixed id format (B4).
 */
public record ProcessorAuthorisationId(String value) {

    public ProcessorAuthorisationId {
        IdFormat.require("processorAuthorisationId", value);
    }

    public static ProcessorAuthorisationId parse(String value) {
        return new ProcessorAuthorisationId(value);
    }
}
