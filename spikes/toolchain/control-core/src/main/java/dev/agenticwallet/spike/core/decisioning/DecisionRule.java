package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;

/** A fixed, testable rule over a proposed amount already converted to the base currency. */
@FunctionalInterface
public interface DecisionRule {
    Verdict judge(Money amountInBaseCurrency);
}
