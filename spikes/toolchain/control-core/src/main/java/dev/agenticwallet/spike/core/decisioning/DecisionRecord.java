package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;
import java.time.Instant;
import java.util.UUID;

/** What the control layer stores about one decision (a slice of the real replay unit). */
public record DecisionRecord(UUID id, Instant decidedAt, Money amount, Verdict verdict) {}
