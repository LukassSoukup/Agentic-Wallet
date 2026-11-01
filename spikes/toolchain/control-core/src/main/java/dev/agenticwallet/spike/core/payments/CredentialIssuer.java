package dev.agenticwallet.spike.core.payments;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;

/**
 * Port: the network's single-use credential for exactly one approved decision and amount.
 *
 * <p>Inputs: the decision id (the network's reference) and the approved amount. Failure modes:
 * {@link CredentialResult.Refused} with a code; {@link CredentialResult.NoAnswer} on timeout or
 * transport failure. Idempotency: none; the caller commits {@code CREDENTIAL_REQUESTED} before
 * calling and never calls twice for one decision (06-consistency §3), whatever the result.
 */
public interface CredentialIssuer {

    CredentialResult request(DecisionId decisionId, Money amount);

    sealed interface CredentialResult {
        record Issued(Credential credential) implements CredentialResult {}

        record Refused(String reasonCode) implements CredentialResult {}

        record NoAnswer() implements CredentialResult {}
    }
}
