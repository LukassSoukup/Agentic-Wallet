package dev.agenticwallet.spike.backend.payments;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Addresses and timeouts of {@code payments}' remote calls (ADR-0009 B5 and B6): every call has an
 * explicit connect and read timeout; the commit bound is added to each call's deadline on the
 * attempt row; the resolver runs at the interval; the session lifetime is what the demo shop grants
 * (the real event will carry the stored expiry). No defaults for addresses.
 */
@Validated
@ConfigurationProperties("skeleton.payments")
public record PaymentsProperties(
        @NotNull String demoShopUrl,
        @NotNull String networkUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration credentialTimeout,
        @NotNull Duration completionTimeout,
        @NotNull Duration commitBound,
        @NotNull Duration resolverInterval,
        @NotNull Duration sessionLifetime) {

    /**
     * The listener's own true bound (B6): three transactions plus the two calls plus a margin of
     * one commit bound. The staleness monitor's processing bound must exceed it.
     */
    public Duration listenerDeadline(Duration poolAcquisition, Duration transactionTimeout) {
        return poolAcquisition
                .plus(transactionTimeout)
                .multipliedBy(3)
                .plus(credentialTimeout)
                .plus(completionTimeout)
                .plus(commitBound);
    }
}
