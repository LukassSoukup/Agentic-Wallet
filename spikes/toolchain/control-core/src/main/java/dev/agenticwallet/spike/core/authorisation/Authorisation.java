package dev.agenticwallet.spike.core.authorisation;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;

/** One card authorisation as the processor's hook delivers it, already typed at the edge. */
public record Authorisation(
        String processorAuthorisationId, Money amount, MerchantReference merchant, Instant at) {}
