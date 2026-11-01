package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;
import java.math.BigDecimal;
import java.util.Currency;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;

/** jetCheck: the decider is a function of its input and never approves what a hard rule forbids. */
class DeciderPropertyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private static final Generator<Money> AMOUNTS =
            Generator.integers(0, 30_000)
                    .map(cents -> new Money(BigDecimal.valueOf(cents, 2), EUR));

    private static final Generator<DecisionInput> INPUTS =
            Generator.from(env -> {
                MandateStatus status = env.generate(Generator.sampledFrom(MandateStatus.values()));
                MandateTerms mandate =
                        new MandateTerms(
                                DecisionInputs.MANDATE,
                                1,
                                status,
                                env.generate(AMOUNTS),
                                env.generate(AMOUNTS));
                LedgerSnapshot ledger =
                        new LedgerSnapshot(env.generate(AMOUNTS), env.generate(AMOUNTS));
                return DecisionInputs.input(
                        mandate, env.generate(AMOUNTS).amount().toPlainString(), ledger);
            });

    @Test
    void decidingTwiceGivesTheSameVerdict() {
        PropertyChecker.forAll(
                INPUTS, input -> Decider.decide(input).equals(Decider.decide(input)));
    }

    @Test
    void neverApprovesOverTheLimitOverTheBudgetOrARevokedMandate() {
        PropertyChecker.forAll(
                INPUTS,
                input -> {
                    boolean forbidden =
                            input.mandate().status() != MandateStatus.ACTIVE
                                    || input.cart()
                                            .total()
                                            .isGreaterThan(input.mandate().limitPerPurchase())
                                    || input.cart()
                                            .total()
                                            .isGreaterThan(
                                                    input.ledger()
                                                            .remainingOf(input.mandate().budget()));
                    Outcome outcome = Decider.decide(input).outcome();
                    return forbidden ? outcome == Outcome.DECLINE : outcome == Outcome.APPROVE;
                });
    }
}
