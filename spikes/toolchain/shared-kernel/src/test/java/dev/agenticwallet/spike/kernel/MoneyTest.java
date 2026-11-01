package dev.agenticwallet.spike.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsHalfEvenToTwoDecimals() {
        assertThat(Money.of("1.005", "EUR").amount()).isEqualByComparingTo("1.00");
        assertThat(Money.of("1.015", "EUR").amount()).isEqualByComparingTo("1.02");
        assertThat(Money.of("1.0251", "EUR").amount()).isEqualByComparingTo("1.03");
    }

    @Test
    void convertsThroughRateWithOneRounding() {
        Money czk = Money.of("100.00", "CZK");
        Money eur = czk.convertedAt(new BigDecimal("0.04125"), Currency.getInstance("EUR"));
        assertThat(eur.amount()).isEqualByComparingTo("4.12");
        assertThat(eur.currency().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void refusesToCompareAcrossCurrencies() {
        assertThatThrownBy(() -> Money.of("1.00", "EUR").isGreaterThan(Money.of("1.00", "CZK")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("CZK");
    }
}
