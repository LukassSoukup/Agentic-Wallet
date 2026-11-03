package dev.agenticwallet.spike.simulators.demoshop;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** The demo shop's two SYNTHETIC items with fixed EUR prices. */
final class Catalogue {

    static final String CURRENCY = "EUR";

    private static final Map<String, BigDecimal> UNIT_PRICES =
            Map.of("SYN-0001", new BigDecimal("29.90"), "SYN-0002", new BigDecimal("89.00"));

    private Catalogue() {}

    static Optional<BigDecimal> unitPriceOf(String merchantItemId) {
        return Optional.ofNullable(UNIT_PRICES.get(merchantItemId));
    }
}
