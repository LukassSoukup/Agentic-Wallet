package dev.agenticwallet.spike.simulators.demoshop;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Faults a scenario switches on: a completion that answers late (the merchant did complete, the
 * caller's read timeout fired first), so the backend must end in UNKNOWN and resolve by lookup.
 */
@Component
class DemoShopFaults {

    private final AtomicReference<Duration> completionDelay = new AtomicReference<>(Duration.ZERO);

    Duration completionDelay() {
        return completionDelay.get();
    }

    void delayCompletionsBy(Duration delay) {
        completionDelay.set(delay);
    }

    void reset() {
        completionDelay.set(Duration.ZERO);
    }
}
