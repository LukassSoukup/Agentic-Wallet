package dev.agenticwallet.spike.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * An exact amount in one currency, scale 2, half-even (invariant 6).
 *
 * <p>Spike version: enough to prove jetCheck and the toolchain on Java 25; T-003 owns the real
 * type.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("amount and currency are required");
        }
        amount = amount.setScale(SCALE, ROUNDING);
    }

    /** Parses a decimal string; never from a double. */
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /** Converts with an FX rate, rounding half-even to scale 2 exactly once. */
    public Money convertedAt(BigDecimal rate, Currency target) {
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("FX rate must be positive");
        }
        return new Money(amount.multiply(rate), target);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
