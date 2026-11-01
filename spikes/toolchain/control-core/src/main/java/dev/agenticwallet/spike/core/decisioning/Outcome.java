package dev.agenticwallet.spike.core.decisioning;

/** The closed set of decisions (invariant 1), each with an explicit caution rank. */
public enum Outcome {
    APPROVE(0),
    STEP_UP(1),
    DECLINE(2);

    private final int cautionRank;

    Outcome(int cautionRank) {
        this.cautionRank = cautionRank;
    }

    /** The more cautious of two outcomes; the combiner only ever moves this way (invariant 2). */
    public Outcome mostCautiousWith(Outcome other) {
        return isAtLeastAsCautiousAs(other) ? this : other;
    }

    public boolean isAtLeastAsCautiousAs(Outcome other) {
        return cautionRank >= other.cautionRank;
    }
}
