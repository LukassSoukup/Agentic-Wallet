package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.CheckoutAttemptId;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;
import java.util.Optional;

/** One attempt row as read; the state machine itself is {@link AttemptState}. */
public record CheckoutAttempt(
        CheckoutAttemptId id,
        DecisionId decisionId,
        MandateId mandateId,
        AttemptState state,
        String checkoutSessionId,
        Instant sessionExpiresAt,
        MerchantReference merchant,
        Money amount,
        Optional<Instant> callDeadline) {}
