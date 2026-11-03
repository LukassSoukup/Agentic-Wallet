package dev.agenticwallet.spike.hook;

import dev.agenticwallet.spike.kernel.ids.DecisionId;
import java.util.Optional;

/** What the responder tells the processor: a match with the pre-approval it consumed, or not. */
record HookAnswer(Verdict answer, Optional<DecisionId> preApproval, HookProperties.Mode mode) {

    enum Verdict {
        MATCH,
        NO_MATCH
    }

    static HookAnswer match(DecisionId preApproval, HookProperties.Mode mode) {
        return new HookAnswer(Verdict.MATCH, Optional.of(preApproval), mode);
    }

    static HookAnswer noMatch(HookProperties.Mode mode) {
        return new HookAnswer(Verdict.NO_MATCH, Optional.empty(), mode);
    }
}
