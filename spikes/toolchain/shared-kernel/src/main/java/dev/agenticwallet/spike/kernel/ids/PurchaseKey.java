package dev.agenticwallet.spike.kernel.ids;

/**
 * The agent's idempotency key for one intended purchase, generated once and reused on every retry
 * (06-consistency §5). Unique together with the agent instance, never alone.
 */
public record PurchaseKey(String value) {

    public PurchaseKey {
        IdFormat.require("purchaseKey", value);
    }

    public static PurchaseKey parse(String value) {
        return new PurchaseKey(value);
    }
}
