package dev.agenticwallet.spike.core.decisioning;

import java.time.Instant;

/**
 * The replay unit (glossary, 06-consistency §6): everything {@link Decider#decide} reads, stored
 * with the decision. {@code decide(storedInput)} must return the stored verdict without any
 * adapter. The skeleton carries the slice its three rules need; T-003 writes the full record.
 */
public record DecisionInput(
        int schemaVersion,
        String ruleSetVersion,
        Proposal proposal,
        MandateTerms mandate,
        CartEvidence cart,
        LedgerSnapshot ledger,
        Instant decidedAt) {
    public static final int SCHEMA_VERSION = 1;
}
