package dev.agenticwallet.spike.kernel.ids;

import java.util.UUID;

/**
 * One instance of the shopping agent for one cardholder; comes from the verified credential, never
 * from a request body (ADR-0006).
 */
public record AgentInstanceId(UUID value) {

    public AgentInstanceId {
        if (value == null) {
            throw new IllegalArgumentException("AgentInstanceId requires a value");
        }
    }

    public static AgentInstanceId parse(String value) {
        try {
            return new AgentInstanceId(UUID.fromString(IdFormat.require("AgentInstanceId", value)));
        } catch (IllegalArgumentException notAUuid) {
            throw new InvalidIdFormatException("AgentInstanceId");
        }
    }
}
