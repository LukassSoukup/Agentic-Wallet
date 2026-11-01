package dev.agenticwallet.spike.simulators.demoshop;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory sessions of the SYNTHETIC demo shop; a simulator keeps nothing across restarts. */
@Component
class CheckoutSessions {

    static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);

    private final Map<String, CheckoutSession> sessions = new ConcurrentHashMap<>();

    Optional<CheckoutSession> open(String merchantReference, List<OrderLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            Optional<BigDecimal> unitPrice = Catalogue.unitPriceOf(line.merchantItemId());
            if (unitPrice.isEmpty()) {
                return Optional.empty();
            }
            total = total.add(unitPrice.get().multiply(BigDecimal.valueOf(line.quantity())));
        }
        CheckoutSession session =
                new CheckoutSession(
                        "syn-session-" + UUID.randomUUID(),
                        merchantReference,
                        total,
                        Instant.now().plus(SESSION_LIFETIME));
        sessions.put(session.id(), session);
        return Optional.of(session);
    }

    Optional<CheckoutSession> find(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    record OrderLine(String merchantItemId, int quantity) {}
}
