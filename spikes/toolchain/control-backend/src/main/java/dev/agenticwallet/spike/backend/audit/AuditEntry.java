package dev.agenticwallet.spike.backend.audit;

import dev.agenticwallet.spike.core.decisioning.DecisionInput;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import java.util.UUID;

/**
 * One decision as the audit keeps it: the owner, the caller, the verdict and the complete input.
 */
public record AuditEntry(
        DecisionId decisionId,
        UUID cardholderId,
        AgentInstanceId agentInstance,
        Verdict verdict,
        DecisionInput input) {}
