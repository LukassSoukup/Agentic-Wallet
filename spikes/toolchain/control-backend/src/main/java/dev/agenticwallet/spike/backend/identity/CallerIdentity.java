package dev.agenticwallet.spike.backend.identity;

import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import java.util.UUID;

/** Who is calling, from the verified credential and never from the body (ADR-0006). */
public record CallerIdentity(UUID cardholderId, AgentInstanceId agentInstance) {}
