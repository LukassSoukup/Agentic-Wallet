package dev.agenticwallet.spike.core.decisioning;

/** Only an active mandate can approve anything; the status is in the input, so a replay sees it. */
final class MandateStatusRule implements DecisionRule {

    @Override
    public Verdict judge(DecisionInput input) {
        return switch (input.mandate().status()) {
            case ACTIVE -> Verdict.of(Outcome.APPROVE, ReasonCode.MANDATE_ACTIVE);
            case REVOKED -> Verdict.of(Outcome.DECLINE, ReasonCode.MANDATE_NOT_ACTIVE);
        };
    }
}
