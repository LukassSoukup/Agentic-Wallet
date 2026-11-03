package dev.agenticwallet.spike.hook;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Shadow or enforcing (glossary: hook responder); the answer is computed and recorded in both. */
@Validated
@ConfigurationProperties("skeleton.hook")
record HookProperties(@NotNull Mode mode) {

    enum Mode {
        SHADOW,
        ENFORCING
    }
}
