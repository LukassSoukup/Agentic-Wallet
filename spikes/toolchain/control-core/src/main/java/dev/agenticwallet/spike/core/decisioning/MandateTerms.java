package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.MandateId;

/**
 * The confirmed mandate version a decision is made under, copied in full into the {@link
 * DecisionInput} so a replay needs no lookup (06-consistency §6). Both amounts are in the base
 * currency.
 */
public record MandateTerms(
        MandateId id, int version, MandateStatus status, Money limitPerPurchase, Money budget) {}
