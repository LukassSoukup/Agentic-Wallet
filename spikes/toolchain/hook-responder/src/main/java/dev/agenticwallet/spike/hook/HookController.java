package dev.agenticwallet.spike.hook;

import dev.agenticwallet.spike.core.authorisation.Authorisation;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.InvalidIdFormatException;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import dev.agenticwallet.spike.kernel.ids.ProcessorAuthorisationId;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The processor's hook, in the skeleton's own synthetic profile (a real profile is the processor's,
 * ADR-0009 B7). Types the request at the edge; no outbound call on this path.
 */
@RestController
@RequestMapping("/hook/authorisations")
class HookController {

    private final HookService service;

    HookController(HookService service) {
        this.service = service;
    }

    record HookRequest(
            String processorAuthorisationId,
            BigDecimal amount,
            String currency,
            String merchantReference,
            Instant occurredAt) {}

    record HookAnswerView(
            String answer, UUID preApprovalId, String processorAuthorisationId, String mode) {}

    @PostMapping
    HookAnswerView authorise(@RequestBody HookRequest request) {
        if (request.amount() == null
                || request.currency() == null
                || request.occurredAt() == null) {
            throw new MalformedAuthorisationException();
        }
        Authorisation authorisation =
                new Authorisation(
                        ProcessorAuthorisationId.parse(request.processorAuthorisationId()).value(),
                        new Money(request.amount(), currencyOf(request.currency())),
                        MerchantReference.parse(request.merchantReference()),
                        request.occurredAt());
        HookAnswer answer = service.answer(authorisation, Instant.now());
        return new HookAnswerView(
                answer.answer().name(),
                answer.preApproval().map(DecisionId::value).orElse(null),
                authorisation.processorAuthorisationId(),
                answer.mode().name());
    }

    /**
     * A missing amount, currency or time, or an unknown currency, is a problem at the edge, never a
     * 500.
     */
    @ExceptionHandler(MalformedAuthorisationException.class)
    ResponseEntity<ProblemDetail> malformed(MalformedAuthorisationException malformed) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "The authorisation is not well formed.");
        problem.setType(URI.create("urn:agentic-wallet:hook:malformed-authorisation"));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidIdFormatException.class)
    ResponseEntity<ProblemDetail> badId(InvalidIdFormatException badFormat) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, badFormat.getMessage());
        problem.setType(URI.create("urn:agentic-wallet:hook:invalid-id-format"));
        return ResponseEntity.badRequest().body(problem);
    }

    private static Currency currencyOf(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException unknownCurrency) {
            throw new MalformedAuthorisationException();
        }
    }

    /** The processor's request lacks a field or names an unknown currency; nothing is echoed. */
    static final class MalformedAuthorisationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        MalformedAuthorisationException() {
            super("the authorisation is not well formed");
        }
    }
}
