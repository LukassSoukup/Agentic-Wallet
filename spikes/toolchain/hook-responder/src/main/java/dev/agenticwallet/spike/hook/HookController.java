package dev.agenticwallet.spike.hook;

import dev.agenticwallet.spike.core.authorisation.Authorisation;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.InvalidIdFormatException;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
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
        Authorisation authorisation =
                new Authorisation(
                        requireIdFormat(request.processorAuthorisationId()),
                        new Money(request.amount(), Currency.getInstance(request.currency())),
                        MerchantReference.parse(request.merchantReference()),
                        request.occurredAt());
        HookAnswer answer = service.answer(authorisation, Instant.now());
        return new HookAnswerView(
                answer.answer().name(),
                answer.preApproval().map(DecisionId::value).orElse(null),
                authorisation.processorAuthorisationId(),
                answer.mode().name());
    }

    @ExceptionHandler(InvalidIdFormatException.class)
    ResponseEntity<ProblemDetail> badId(InvalidIdFormatException badFormat) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, badFormat.getMessage());
        problem.setType(URI.create("urn:agentic-wallet:hook:invalid-id-format"));
        return ResponseEntity.badRequest().body(problem);
    }

    /** The processor's id has the fixed id format too; it is logged, so it is checked (B4). */
    private static String requireIdFormat(String processorAuthorisationId) {
        return MerchantReference.parse(processorAuthorisationId).value();
    }
}
