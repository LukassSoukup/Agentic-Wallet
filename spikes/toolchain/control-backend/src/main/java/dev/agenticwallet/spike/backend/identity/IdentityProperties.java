package dev.agenticwallet.spike.backend.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The skeleton's stand-in for {@code identity}'s signed agent session tokens (ADR-0006): fixed
 * tokens from configuration, each bound to a cardholder and an agent instance. The token values are
 * deployment configuration and never in the repository (ADR-0009 B2); start-up fails without one.
 */
@Validated
@ConfigurationProperties("skeleton.identity")
record IdentityProperties(@NotEmpty List<@Valid AgentSession> sessions) {

    record AgentSession(
            @NotBlank String token, @NotNull UUID cardholderId, @NotNull UUID agentInstanceId) {}
}
