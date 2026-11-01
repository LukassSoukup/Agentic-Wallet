package dev.agenticwallet.spike.backend.decisioning;

import dev.agenticwallet.spike.backend.identity.AgentSessions;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.backend.payments.Checkouts;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.Proposal;
import dev.agenticwallet.spike.core.decisioning.ReasonCode;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.UntrustedText;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.InvalidIdFormatException;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantItemId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import dev.agenticwallet.spike.kernel.ids.PurchaseKey;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agent side of the backend's REST API (B7: {@code /api/v1/agent}), called by {@code
 * control-mcp}. The caller is the bearer token's agent instance; ids are parsed at this edge and a
 * bad one is a 400 problem that never echoes the value; every expected failure is an RFC 9457
 * problem (B3).
 */
@RestController
@RequestMapping("/api/v1/agent")
class AgentProposalController {

    private static final String BEARER = "Bearer ";
    private static final String PROBLEM_TYPE = "urn:agentic-wallet:control-api:";

    private final AgentSessions sessions;
    private final ProposalService proposals;
    private final DecisionStore decisions;
    private final Checkouts checkouts;

    AgentProposalController(
            AgentSessions sessions,
            ProposalService proposals,
            DecisionStore decisions,
            Checkouts checkouts) {
        this.sessions = sessions;
        this.proposals = proposals;
        this.decisions = decisions;
        this.checkouts = checkouts;
    }

    record LineRequest(String merchantItemId, int quantity) {}

    record ProposalRequest(
            String purchaseKey,
            String mandateId,
            String merchantReference,
            List<LineRequest> lines,
            String description) {}

    record DecisionView(
            UUID decisionId,
            String outcome,
            List<ReasonCode> reasons,
            Optional<AttemptState> checkout) {}

    @PostMapping("/proposals")
    ResponseEntity<?> propose(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ProposalRequest request) {
        Optional<CallerIdentity> caller = callerOf(authorization);
        if (caller.isEmpty()) {
            return problem(
                    HttpStatus.UNAUTHORIZED,
                    "unknown-agent-session",
                    "The agent session is not known.");
        }
        Proposal proposal = parse(caller.get(), request);
        return switch (proposals.propose(caller.get(), proposal)) {
            case ProposalOutcome.Decided decided ->
                    ResponseEntity.ok(view(decided.decisionId(), decided.verdict()));
            case ProposalOutcome.PurchaseKeyConflict ignored ->
                    problem(
                            HttpStatus.CONFLICT,
                            "purchase-key-conflict",
                            "This purchaseKey was used for a different proposal.");
            case ProposalOutcome.UnknownMandate ignored ->
                    problem(HttpStatus.NOT_FOUND, "unknown-mandate", "The mandate is not known.");
            case ProposalOutcome.MerchantUnavailable unavailable ->
                    problem(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            unavailable.reasonCode(),
                            "This merchant can't be bought from by an agent right now.");
        };
    }

    @GetMapping("/decisions/{decisionId}")
    ResponseEntity<?> decision(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String decisionId) {
        if (callerOf(authorization).isEmpty()) {
            return problem(
                    HttpStatus.UNAUTHORIZED,
                    "unknown-agent-session",
                    "The agent session is not known.");
        }
        DecisionId id = DecisionId.parse(decisionId);
        return decisions
                .find(id)
                .<ResponseEntity<?>>map(record -> ResponseEntity.ok(view(id, record.verdict())))
                .orElseGet(() ->
                        problem(HttpStatus.NOT_FOUND, "unknown-decision", "No such decision."));
    }

    @ExceptionHandler(InvalidIdFormatException.class)
    ResponseEntity<ProblemDetail> badId(InvalidIdFormatException badFormat) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-id-format", badFormat.getMessage());
    }

    private Optional<CallerIdentity> callerOf(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return Optional.empty();
        }
        return sessions.resolve(authorization.substring(BEARER.length()));
    }

    private static Proposal parse(CallerIdentity caller, ProposalRequest request) {
        return new Proposal(
                caller.agentInstance(),
                PurchaseKey.parse(request.purchaseKey()),
                MandateId.parse(request.mandateId()),
                MerchantReference.parse(request.merchantReference()),
                request.lines().stream()
                        .map(line ->
                                new OrderLine(
                                        MerchantItemId.parse(line.merchantItemId()),
                                        line.quantity()))
                        .toList(),
                UntrustedText.of(request.description() == null ? "" : request.description()));
    }

    private DecisionView view(DecisionId id, Verdict verdict) {
        return new DecisionView(
                id.value(),
                verdict.outcome().name(),
                verdict.reasons(),
                checkouts.attemptStateOf(id));
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE + type));
        return ResponseEntity.status(status).body(problem);
    }
}
