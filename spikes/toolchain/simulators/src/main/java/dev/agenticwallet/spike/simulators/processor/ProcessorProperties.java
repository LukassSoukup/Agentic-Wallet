package dev.agenticwallet.spike.simulators.processor;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the SYNTHETIC processor sends authorisations and how long it waits (the real hook budget is
 * 800 ms to 2 s; the Pi measurement uses the budget as the timeout). No silent default for the
 * address (ADR-0009 B5).
 */
@Validated
@ConfigurationProperties("skeleton.processor")
record ProcessorProperties(@NotNull String hookResponderUrl, @NotNull Duration hookTimeout) {}
