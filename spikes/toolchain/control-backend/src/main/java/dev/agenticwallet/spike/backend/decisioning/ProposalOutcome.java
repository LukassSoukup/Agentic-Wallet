package dev.agenticwallet.spike.backend.decisioning;

import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.ids.DecisionId;

/** What a proposal ends in; expected failures are values (ADR-0009 B3). */
public sealed interface ProposalOutcome {

    /** A decision, new or the stored one for a redelivered key. */
    record Decided(DecisionId decisionId, Verdict verdict, boolean redelivered)
            implements ProposalOutcome {}

    /** The key was seen with different binding fields: nothing is decided (06-consistency §5). */
    record PurchaseKeyConflict() implements ProposalOutcome {}

    /** The mandate does not exist for this caller. */
    record UnknownMandate() implements ProposalOutcome {}

    /** No checkout session could be opened: nothing binding exists to decide on (ADR-0007). */
    record MerchantUnavailable(String reasonCode) implements ProposalOutcome {}
}
