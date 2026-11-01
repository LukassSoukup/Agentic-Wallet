package dev.agenticwallet.spike.core.decisioning;

/** Reason codes a verdict carries; every decision is explained (invariant 1). */
public enum ReasonCode {
    MANDATE_ACTIVE,
    MANDATE_NOT_ACTIVE,
    WITHIN_LIMIT,
    OVER_LIMIT,
    WITHIN_BUDGET,
    OVER_BUDGET
}
