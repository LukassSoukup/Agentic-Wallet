package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.kernel.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles attempts whose announced call is past its deadline and every UNKNOWN one by reading the
 * merchant's session status (B6: after the deadline only the resolver acts). Reads the {@code
 * Clock} port; acts through compare-and-set, so a duplicate run is harmless.
 */
@Component
class AttemptResolverJob {

    private final CheckoutAttempts attempts;
    private final CheckoutAttemptService service;
    private final Clock clock;

    AttemptResolverJob(CheckoutAttempts attempts, CheckoutAttemptService service, Clock clock) {
        this.attempts = attempts;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${skeleton.payments.resolver-interval}")
    void resolveDueAttempts() {
        for (CheckoutAttempt attempt : attempts.awaitingResolution(clock.now())) {
            service.resolve(attempt);
        }
    }
}
