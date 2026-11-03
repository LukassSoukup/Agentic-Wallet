package dev.agenticwallet.spike.simulators.network;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The SYNTHETIC network: request a credential for an approved decision; read how often it was
 * asked.
 */
@RestController
@RequestMapping("/network/credentials")
class CredentialController {

    private final CredentialIssuer issuer;

    CredentialController(CredentialIssuer issuer) {
        this.issuer = issuer;
    }

    record CredentialRequest(UUID decisionId, BigDecimal amount, String currency) {}

    record CredentialView(String credential, boolean synthetic) {}

    record RequestCountView(UUID decisionId, int requests) {}

    @PostMapping
    CredentialView issue(@RequestBody CredentialRequest request) {
        return new CredentialView(issuer.issueFor(request.decisionId()), true);
    }

    @GetMapping("/{decisionId}/requests")
    RequestCountView requests(@PathVariable UUID decisionId) {
        return new RequestCountView(decisionId, issuer.requestsFor(decisionId));
    }
}
