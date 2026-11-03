package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;

/**
 * The budget period's totals as read under the ledger's row lock at decision time (06-consistency
 * §1). Only approved purchases are in here (invariant 5): a pending step-up is neither reserved nor
 * counted.
 */
public record LedgerSnapshot(Money reserved, Money counted) {

    public Money remainingOf(Money budget) {
        return budget.minus(reserved).minus(counted);
    }
}
