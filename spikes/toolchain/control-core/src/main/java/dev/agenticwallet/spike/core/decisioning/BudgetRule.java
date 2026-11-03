package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;

/**
 * Declines a cart total that does not fit the budget period's remaining amount, where remaining is
 * budget minus reserved minus counted (06-consistency §1). Exactly the remaining amount fits.
 */
final class BudgetRule implements DecisionRule {

    @Override
    public Verdict judge(DecisionInput input) {
        Money remaining = input.ledger().remainingOf(input.mandate().budget());
        if (input.cart().total().isGreaterThan(remaining)) {
            return Verdict.of(Outcome.DECLINE, ReasonCode.OVER_BUDGET);
        }
        return Verdict.of(Outcome.APPROVE, ReasonCode.WITHIN_BUDGET);
    }
}
