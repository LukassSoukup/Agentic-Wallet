package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;
import java.time.Instant;

/**
 * Tier-1 evidence (ADR-0007): the merchant's priced cart from the checkout session the backend
 * opened, with the total already in the base currency, and the session's stored expiry.
 */
public record CartEvidence(String checkoutSessionId, Money total, Instant expiresAt) {}
