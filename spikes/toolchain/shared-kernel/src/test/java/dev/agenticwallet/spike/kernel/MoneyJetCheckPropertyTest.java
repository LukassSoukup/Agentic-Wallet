package dev.agenticwallet.spike.kernel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.jetbrains.jetCheck.PropertyFalsified;
import org.junit.jupiter.api.Test;

/**
 * The same properties as {@link MoneyPropertyTest}, written with jetCheck: the fallback the T-008
 * report proposes for jqwik. Plain JUnit tests; jetCheck is a library, not a test engine.
 */
class MoneyJetCheckPropertyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    /** Amounts between -200,000.0000 and 200,000.0000 with four decimals. */
    private static final Generator<Money> EUROS =
            Generator.from(env -> {
                int tenThousandths =
                        env.generate(Generator.integers(-2_000_000_000, 2_000_000_000));
                return new Money(BigDecimal.valueOf(tenThousandths, 4), EUR);
            });

    @Test
    void scaleIsAlwaysTwo() {
        PropertyChecker.forAll(EUROS, money -> money.amount().scale() == Money.SCALE);
    }

    @Test
    void additionIsCommutative() {
        PropertyChecker.forAll(
                Generator.zipWith(EUROS, EUROS, Pair::new),
                pair -> pair.a().plus(pair.b()).equals(pair.b().plus(pair.a())));
    }

    @Test
    void conversionAtRateOneKeepsTheAmount() {
        PropertyChecker.forAll(
                EUROS,
                money ->
                        money.convertedAt(BigDecimal.ONE, Currency.getInstance("CZK"))
                                        .amount()
                                        .compareTo(money.amount())
                                == 0);
    }

    /**
     * A deliberately false property (amounts never exceed 500): jetCheck must report a shrunk
     * counterexample, not the first random one. Finding (T-008): with a mapped integer generator
     * the shrink stops early (hundreds, not 500.01); jqwik shrinks the same property to 500.01.
     */
    @Test
    void shrinksACounterexample() {
        assertThatThrownBy(() ->
                        PropertyChecker.customized()
                                .withIterationCount(200)
                                .forAll(
                                        EUROS,
                                        money -> !money.isGreaterThan(Money.of("500.00", "EUR"))))
                .isInstanceOf(PropertyFalsified.class)
                .hasMessageContaining("Falsified on Money")
                .hasMessageContaining("Shrunk in");
    }

    private record Pair(Money a, Money b) {}
}
