package dev.agenticwallet.spike.backend.identity;

import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Published API of {@code identity}: resolves a bearer token to the caller. A token that is not
 * known resolves to nothing; the token itself is never logged or echoed.
 */
@Component
public class AgentSessions {

    private final Map<String, CallerIdentity> byToken;

    AgentSessions(IdentityProperties properties) {
        this.byToken =
                properties.sessions().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        IdentityProperties.AgentSession::token,
                                        session ->
                                                new CallerIdentity(
                                                        session.cardholderId(),
                                                        new AgentInstanceId(
                                                                session.agentInstanceId()))));
    }

    public Optional<CallerIdentity> resolve(String bearerToken) {
        return Optional.ofNullable(byToken.get(bearerToken));
    }
}
