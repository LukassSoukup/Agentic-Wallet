package dev.agenticwallet.spike.kernel.ids;

/** A merchant's own id of an item, as the agent proposes it; fixed id format, never free text. */
public record MerchantItemId(String value) {

    public MerchantItemId {
        IdFormat.require("merchantItemId", value);
    }

    public static MerchantItemId parse(String value) {
        return new MerchantItemId(value);
    }
}
