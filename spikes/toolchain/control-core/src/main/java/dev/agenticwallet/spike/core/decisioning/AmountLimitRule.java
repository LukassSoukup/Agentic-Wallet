package dev.agenticwallet.spike.core.decisioning;

/** Declines a cart total above the mandate's limit per purchase; one cent over is over. */
final class AmountLimitRule implements DecisionRule {

    @Override
    public Verdict judge(DecisionInput input) {
        if (input.cart().total().isGreaterThan(input.mandate().limitPerPurchase())) {
            return Verdict.of(Outcome.DECLINE, ReasonCode.OVER_LIMIT);
        }
        return Verdict.of(Outcome.APPROVE, ReasonCode.WITHIN_LIMIT);
    }
}
