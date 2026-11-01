package dev.agenticwallet.spike.backend.support;

import dev.agenticwallet.spike.kernel.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A test adapter of the {@code Clock} port: real time plus an offset. Time keeps moving, so
 * deadlines pass; the offset lets each test own a budget period (a month) of its own.
 */
public final class OffsetClock implements Clock {

    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    @Override
    public Instant now() {
        return Instant.now().plus(offset.get());
    }

    public void shiftBy(Duration shift) {
        offset.set(shift);
    }
}
