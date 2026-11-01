package dev.agenticwallet.spike.simulators.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * The network's credential step, SYNTHETIC: issues a single-use credential per request and counts
 * requests per decision, which is how a scenario proves "at most one credential request per
 * decision" from outside the backend (06-consistency §3).
 */
@Component
class CredentialIssuer {

    private final Map<UUID, AtomicInteger> requestsByDecision = new ConcurrentHashMap<>();

    String issueFor(UUID decisionId) {
        requestsByDecision.computeIfAbsent(decisionId, id -> new AtomicInteger()).incrementAndGet();
        return "syn-credential-" + UUID.randomUUID();
    }

    int requestsFor(UUID decisionId) {
        AtomicInteger count = requestsByDecision.get(decisionId);
        return count == null ? 0 : count.get();
    }
}
