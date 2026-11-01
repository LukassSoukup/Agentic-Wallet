package dev.agenticwallet.spike.core.authorisation;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;

/**
 * Written with an APPROVE in the same transaction (glossary): what the hook matches an
 * authorisation against. The window is closed at both ends.
 */
public record PreApproval(
        DecisionId decisionId,
        Money amount,
        MerchantReference merchant,
        Instant validFrom,
        Instant validUntil) {}
