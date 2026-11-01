package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;

/** Declines any amount above the limit; one cent over is over. */
public record AmountLimitRule(Money limit) implements DecisionRule {

    @Override
    public Verdict judge(Money amountInBaseCurrency) {
        if (amountInBaseCurrency.isGreaterThan(limit)) {
            return Verdict.of(Outcome.DECLINE, ReasonCode.OVER_LIMIT);
        }
        return Verdict.of(Outcome.APPROVE, ReasonCode.WITHIN_LIMIT);
    }
}
