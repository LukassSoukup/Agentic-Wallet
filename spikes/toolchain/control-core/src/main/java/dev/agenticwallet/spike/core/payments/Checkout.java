package dev.agenticwallet.spike.core.payments;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;
import java.util.List;

/**
 * Port: the merchant's protocol checkout session (ADR-0006, 06-consistency §3).
 *
 * <p>Inputs: a merchant from the directory and typed order lines; a session id the merchant gave
 * us; a credential and our idempotency key. Failure modes are values: {@link
 * OpenResult.Unavailable} (no session could be opened: DECLINE "can't buy here"), {@link
 * CompletionResult.Refused} (the merchant refused, with a code), {@link CompletionResult.NoAnswer}
 * (timeout or transport failure: UNKNOWN, never FAILED), {@link SessionStatus.NotFound} and {@link
 * SessionStatus.NoAnswer}. Idempotency: {@code openSession} is a side effect at the merchant (no
 * payment; the session expires on its own); {@code complete} is idempotent on the key and is called
 * at most once per attempt by the listener, retries happen only through {@code status}; {@code
 * status} is a read. Adapters set explicit connect and read timeouts (ADR-0009 B6) and never retry
 * {@code complete}.
 */
public interface Checkout {

    OpenResult openSession(MerchantReference merchant, List<OrderLine> lines);

    CompletionResult complete(String sessionId, Credential credential, String idempotencyKey);

    SessionStatus status(String sessionId);

    sealed interface OpenResult {
        record Opened(String sessionId, Money total, Instant expiresAt) implements OpenResult {}

        record Unavailable(String reasonCode) implements OpenResult {}
    }

    sealed interface CompletionResult {
        record Completed(String authorisationId) implements CompletionResult {}

        record Refused(String reasonCode) implements CompletionResult {}

        /**
         * The merchant says the session was completed before this call: evidence of completion, not
         * a refusal.
         */
        record AlreadyCompleted() implements CompletionResult {}

        record NoAnswer() implements CompletionResult {}
    }

    sealed interface SessionStatus {
        record Open() implements SessionStatus {}

        record Completed(String authorisationId) implements SessionStatus {}

        record NotFound() implements SessionStatus {}

        record NoAnswer() implements SessionStatus {}
    }
}
