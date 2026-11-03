package dev.agenticwallet.spike.simulators.demoshop;

import dev.agenticwallet.spike.simulators.processor.ProcessorSimulator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demo shop's protocol checkout session, SYNTHETIC: open a priced cart, complete it with a
 * credential (idempotent on the caller's key), read its state. Completing a session makes the
 * shop's payment provider authorise, which the processor simulator relays to the hook responder.
 */
@RestController
@RequestMapping("/sessions")
class CheckoutSessionController {

    private final CheckoutSessions sessions;
    private final DemoShopFaults faults;
    private final ProcessorSimulator processor;

    CheckoutSessionController(
            CheckoutSessions sessions, DemoShopFaults faults, ProcessorSimulator processor) {
        this.sessions = sessions;
        this.faults = faults;
        this.processor = processor;
    }

    record OpenRequest(String merchantReference, List<CheckoutSessions.OrderLine> lines) {}

    record SessionView(
            String sessionId,
            String state,
            BigDecimal total,
            String currency,
            Instant expiresAt,
            boolean synthetic) {}

    record CompleteRequest(String credential, String idempotencyKey) {}

    record CompletionView(
            String sessionId, String state, String authorisationId, boolean synthetic) {}

    @PostMapping
    ResponseEntity<?> open(@RequestBody OpenRequest request) {
        return sessions.open(request.merchantReference(), request.lines())
                .<ResponseEntity<?>>map(session -> ResponseEntity.ok(view(session)))
                .orElseGet(() ->
                        problem(
                                HttpStatus.NOT_FOUND,
                                "unknown-item",
                                "An item is not in the catalogue."));
    }

    @GetMapping("/{sessionId}")
    ResponseEntity<?> read(@PathVariable String sessionId) {
        return sessions.find(sessionId)
                .<ResponseEntity<?>>map(session -> ResponseEntity.ok(view(session)))
                .orElseGet(
                        () -> problem(HttpStatus.NOT_FOUND, "unknown-session", "No such session."));
    }

    @PostMapping("/{sessionId}/complete")
    ResponseEntity<?> complete(@PathVariable String sessionId, @RequestBody CompleteRequest request)
            throws InterruptedException {
        var session = sessions.find(sessionId);
        if (session.isEmpty()) {
            return problem(HttpStatus.NOT_FOUND, "unknown-session", "No such session.");
        }
        if (request.credential() == null || request.credential().isBlank()) {
            return problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "missing-credential",
                    "A credential is required.");
        }
        var completion =
                session.get().complete(request.idempotencyKey(), "syn-auth-" + UUID.randomUUID());
        return switch (completion) {
            case CheckoutSession.Completion.Completed completed -> {
                if (completed.first()) {
                    processor.authorise(session.get(), completed.authorisationId());
                }
                answerLateIfAsked();
                yield ResponseEntity.ok(
                        new CompletionView(
                                sessionId, "COMPLETED", completed.authorisationId(), true));
            }
            case CheckoutSession.Completion.AlreadyCompletedWithAnotherKey ignored ->
                    problem(
                            HttpStatus.CONFLICT,
                            "session-already-completed",
                            "The session was completed before.");
        };
    }

    /**
     * The merchant has completed; only the answer is late. That is what makes the outcome UNKNOWN.
     */
    private void answerLateIfAsked() throws InterruptedException {
        Duration delay = faults.completionDelay();
        if (!delay.isZero()) {
            Thread.sleep(delay);
        }
    }

    private static SessionView view(CheckoutSession session) {
        return new SessionView(
                session.id(),
                session.state().name(),
                session.total(),
                Catalogue.CURRENCY,
                session.expiresAt(),
                true);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(java.net.URI.create("urn:agentic-wallet:demo-shop:" + type));
        return ResponseEntity.status(status).body(problem);
    }
}
