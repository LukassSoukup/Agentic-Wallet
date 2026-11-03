package dev.agenticwallet.spike.kernel.ids;

/** The merchant directory's id of a merchant; agent-supplied, so it has the fixed id format. */
public record MerchantReference(String value) {

    public MerchantReference {
        IdFormat.require("merchantReference", value);
    }

    public static MerchantReference parse(String value) {
        return new MerchantReference(value);
    }
}
