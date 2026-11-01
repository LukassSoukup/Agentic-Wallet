package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;

/** What the control layer stores about one decision; the full replay unit goes to the audit. */
public record DecisionRecord(
        DecisionId id,
        Instant decidedAt,
        MandateId mandateId,
        MerchantReference merchant,
        String checkoutSessionId,
        Money amount,
        Verdict verdict) {}
