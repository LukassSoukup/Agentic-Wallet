package dev.agenticwallet.spike.kernel;

import java.time.Instant;

/**
 * Port: the current time. Adapters read the system clock; the core never does (invariant 9).
 *
 * <p>Inputs: none. Failure modes: none. Idempotency: not applicable, a read.
 */
public interface Clock {
    Instant now();
}
