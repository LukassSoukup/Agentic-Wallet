package dev.agenticwallet.spike.simulators.demoshop;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** One SYNTHETIC checkout session: a priced cart that a credential completes exactly once. */
public final class CheckoutSession {

    enum State {
        OPEN,
        COMPLETED
    }

    private final String id;
    private final String merchantReference;
    private final BigDecimal total;
    private final Instant expiresAt;
    private State state = State.OPEN;
    private String completionKey;
    private String authorisationId;

    CheckoutSession(String id, String merchantReference, BigDecimal total, Instant expiresAt) {
        this.id = id;
        this.merchantReference = merchantReference;
        this.total = total;
        this.expiresAt = expiresAt;
    }

    String id() {
        return id;
    }

    public String merchantReference() {
        return merchantReference;
    }

    public BigDecimal total() {
        return total;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    synchronized State state() {
        return state;
    }

    synchronized Optional<String> authorisationId() {
        return Optional.ofNullable(authorisationId);
    }

    /**
     * Completes the session idempotently on the caller's key: the same key returns the same
     * authorisation; a different key on a completed session is refused.
     */
    synchronized Completion complete(String idempotencyKey, String newAuthorisationId) {
        if (state == State.COMPLETED) {
            return idempotencyKey.equals(completionKey)
                    ? new Completion.Completed(authorisationId, false)
                    : new Completion.AlreadyCompletedWithAnotherKey();
        }
        state = State.COMPLETED;
        completionKey = idempotencyKey;
        authorisationId = newAuthorisationId;
        return new Completion.Completed(authorisationId, true);
    }

    sealed interface Completion {
        record Completed(String authorisationId, boolean first) implements Completion {}

        record AlreadyCompletedWithAnotherKey() implements Completion {}
    }
}
