package dev.agenticwallet.spike.core.decisioning;

/** A fixed, testable rule over the complete decision input; it reads nothing else. */
@FunctionalInterface
public interface DecisionRule {
    Verdict judge(DecisionInput input);
}
