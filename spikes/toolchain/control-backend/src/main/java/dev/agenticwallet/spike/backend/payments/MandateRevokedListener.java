package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.kernel.events.MandateRevoked;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code payments} listens to {@code mandates} through a kernel event: the other half of the pair.
 */
@Component
class MandateRevokedListener {

    private final CheckoutAttemptService attempts;

    MandateRevokedListener(CheckoutAttemptService attempts) {
        this.attempts = attempts;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(MandateRevoked event) {
        attempts.cancelCreatedFor(new MandateId(event.mandateId()));
    }
}
