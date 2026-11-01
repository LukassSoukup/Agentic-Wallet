package dev.agenticwallet.spike.kernel;

import java.util.Currency;

/** Two amounts in different currencies were compared or added without FX conversion. */
public final class CurrencyMismatchException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(Currency left, Currency right) {
        super("currencies differ: " + left.getCurrencyCode() + " vs " + right.getCurrencyCode());
    }
}
