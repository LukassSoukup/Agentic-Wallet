package dev.agenticwallet.spike.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Property tests for Money (quality bar: property tests for money). */
class MoneyPropertyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Provide
    Arbitrary<Money> euros() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-1000000"), new BigDecimal("1000000"))
                .ofScale(4)
                .map(amount -> new Money(amount, EUR));
    }

    @Property
    void scaleIsAlwaysTwo(@ForAll("euros") Money money) {
        assertThat(money.amount().scale()).isEqualTo(Money.SCALE);
    }

    @Property
    void additionIsCommutative(@ForAll("euros") Money a, @ForAll("euros") Money b) {
        assertThat(a.plus(b)).isEqualTo(b.plus(a));
    }

    @Property
    void additionIsAssociative(
            @ForAll("euros") Money a, @ForAll("euros") Money b, @ForAll("euros") Money c) {
        assertThat(a.plus(b).plus(c)).isEqualTo(a.plus(b.plus(c)));
    }

    @Property
    void conversionAtRateOneKeepsTheAmount(@ForAll("euros") Money money) {
        Money same = money.convertedAt(BigDecimal.ONE, Currency.getInstance("CZK"));
        assertThat(same.amount()).isEqualByComparingTo(money.amount());
    }
}
