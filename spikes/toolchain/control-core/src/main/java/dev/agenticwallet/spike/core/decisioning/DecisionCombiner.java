package dev.agenticwallet.spike.core.decisioning;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds every verdict into one decision: the most cautious outcome wins and every reason is kept
 * (invariant 2). Pure; no clock, no randomness, no I/O.
 */
public final class DecisionCombiner {

    private DecisionCombiner() {}

    public static Verdict combine(List<Verdict> verdicts) {
        if (verdicts.isEmpty()) {
            throw new IllegalArgumentException("at least one verdict is required");
        }
        Outcome outcome = Outcome.APPROVE;
        List<ReasonCode> reasons = new ArrayList<>();
        for (Verdict verdict : verdicts) {
            outcome = outcome.mostCautiousWith(verdict.outcome());
            reasons.addAll(verdict.reasons());
        }
        return new Verdict(outcome, reasons);
    }
}
