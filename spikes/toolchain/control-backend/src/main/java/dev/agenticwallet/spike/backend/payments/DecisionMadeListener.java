package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The outbox listener that drives the checkout. Deliberately not
 * {@code @ApplicationModuleListener}: that annotation wraps the whole listener in one transaction,
 * while §3 requires each transition committed before the remote call it announces. The registry
 * still tracks this listener's completion, because it is a transactional event listener.
 */
@Component
class DecisionMadeListener {

    private final CheckoutAttemptService attempts;

    DecisionMadeListener(CheckoutAttemptService attempts) {
        this.attempts = attempts;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(DecisionMade event) {
        if (Outcome.valueOf(event.outcome()) == Outcome.APPROVE) {
            attempts.runFor(event);
        }
    }
}
