package dev.agenticwallet.spike.core.decisioning;

import java.util.List;

/**
 * The pure decision function (invariants 1, 2, 9): every rule judges the same input, the combiner
 * folds the verdicts and can only raise caution. No clock, no randomness, no I/O; given a stored
 * input it returns the stored verdict.
 */
public final class Decider {

    /** Named in every {@link DecisionInput} so a replay knows which rules it compares (B7). */
    public static final String RULE_SET_VERSION = "skeleton-1";

    private static final List<DecisionRule> RULES =
            List.of(new MandateStatusRule(), new AmountLimitRule(), new BudgetRule());

    private Decider() {}

    public static Verdict decide(DecisionInput input) {
        return DecisionCombiner.combine(RULES.stream().map(rule -> rule.judge(input)).toList());
    }
}
