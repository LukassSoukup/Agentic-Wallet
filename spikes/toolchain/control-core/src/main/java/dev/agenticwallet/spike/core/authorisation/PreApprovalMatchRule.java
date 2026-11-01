package dev.agenticwallet.spike.core.authorisation;

/**
 * The shared match rule (ADR-0006): an authorisation matches a pre-approval when its amount is at
 * or below the approved amount in the same currency, at the same merchant, inside the window.
 * Consumption (one match per pre-approval) is the hook responder's table, not this rule.
 */
public final class PreApprovalMatchRule {

    private PreApprovalMatchRule() {}

    public static boolean matches(PreApproval preApproval, Authorisation authorisation) {
        return sameCurrency(preApproval, authorisation)
                && !authorisation.amount().isGreaterThan(preApproval.amount())
                && preApproval.merchant().equals(authorisation.merchant())
                && !authorisation.at().isBefore(preApproval.validFrom())
                && !authorisation.at().isAfter(preApproval.validUntil());
    }

    private static boolean sameCurrency(PreApproval preApproval, Authorisation authorisation) {
        return preApproval.amount().currency().equals(authorisation.amount().currency());
    }
}
