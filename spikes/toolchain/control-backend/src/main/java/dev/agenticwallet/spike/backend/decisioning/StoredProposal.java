package dev.agenticwallet.spike.backend.decisioning;

import dev.agenticwallet.spike.core.decisioning.Proposal;
import dev.agenticwallet.spike.kernel.ids.DecisionId;

/** A proposal already decided: what a redelivery is compared with and answered from. */
record StoredProposal(Proposal proposal, DecisionId decisionId) {}
