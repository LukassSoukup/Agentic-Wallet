package dev.agenticwallet.spike.core.decisioning;

import java.util.List;

/** One rule's answer: an outcome and the reasons for it. */
public record Verdict(Outcome outcome, List<ReasonCode> reasons) {

    public Verdict {
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("a verdict carries at least one reason");
        }
        reasons = List.copyOf(reasons);
    }

    public static Verdict of(Outcome outcome, ReasonCode reason) {
        return new Verdict(outcome, List.of(reason));
    }
}
