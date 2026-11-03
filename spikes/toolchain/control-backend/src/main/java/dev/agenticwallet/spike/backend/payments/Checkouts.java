package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.core.payments.Checkout;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Published API of {@code payments}: open the merchant's session before a decision (04 step 5), and
 * read where an approved decision's checkout stands.
 */
@Component
public class Checkouts {

    private final Checkout checkout;
    private final CheckoutAttempts attempts;

    Checkouts(Checkout checkout, CheckoutAttempts attempts) {
        this.checkout = checkout;
        this.attempts = attempts;
    }

    public Checkout.OpenResult openCheckoutSession(
            MerchantReference merchant, List<OrderLine> lines) {
        return checkout.openSession(merchant, lines);
    }

    public Optional<AttemptState> attemptStateOf(DecisionId decision) {
        return attempts.find(decision).map(CheckoutAttempt::state);
    }
}
