package dev.agenticwallet.spike.kernel;

import dev.agenticwallet.spike.kernel.ids.MerchantItemId;

/**
 * One line of an order as both the proposal and the checkout session know it: an item the merchant
 * identifies and how many. Never personal data, never free text.
 */
public record OrderLine(MerchantItemId merchantItemId, int quantity) {

    public OrderLine {
        if (quantity < 1) {
            throw new IllegalArgumentException("an order line needs a quantity of at least one");
        }
    }
}
