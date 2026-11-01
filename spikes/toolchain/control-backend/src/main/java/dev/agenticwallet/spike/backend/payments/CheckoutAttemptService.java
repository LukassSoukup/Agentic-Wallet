package dev.agenticwallet.spike.backend.payments;

import dev.agenticwallet.spike.backend.authorisation.PreApprovals;
import dev.agenticwallet.spike.backend.ledger.Ledger;
import dev.agenticwallet.spike.backend.mandates.Mandates;
import dev.agenticwallet.spike.core.decisioning.MandateStatus;
import dev.agenticwallet.spike.core.payments.AttemptState;
import dev.agenticwallet.spike.core.payments.Checkout;
import dev.agenticwallet.spike.core.payments.Credential;
import dev.agenticwallet.spike.core.payments.CredentialIssuer;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.events.CheckoutCompleted;
import dev.agenticwallet.spike.kernel.events.CheckoutFailed;
import dev.agenticwallet.spike.kernel.events.CheckoutOutcomeUnknown;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drives the checkout attempt of 06-consistency §3. Each transition is committed in its own
 * transaction before the remote call it announces; a transition that another actor already made
 * ends this actor's work (the fence of ADR-0009 B6). The credential exists only on this thread
 * between the request and the completion call.
 */
@Service
class CheckoutAttemptService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutAttemptService.class);

    private final CheckoutAttempts attempts;
    private final CredentialIssuer credentials;
    private final Checkout checkout;
    private final Ledger ledger;
    private final PreApprovals preApprovals;
    private final Mandates mandates;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final PaymentsProperties properties;

    CheckoutAttemptService(
            CheckoutAttempts attempts,
            CredentialIssuer credentials,
            Checkout checkout,
            Ledger ledger,
            PreApprovals preApprovals,
            Mandates mandates,
            ApplicationEventPublisher events,
            TransactionTemplate transaction,
            Clock clock,
            PaymentsProperties properties) {
        this.attempts = attempts;
        this.credentials = credentials;
        this.checkout = checkout;
        this.ledger = ledger;
        this.preApprovals = preApprovals;
        this.mandates = mandates;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
        this.properties = properties;
    }

    /** The listener's path: create, request one credential, complete, each step fenced. */
    void runFor(DecisionMade approved) {
        DecisionId decision = new DecisionId(approved.decisionId());
        transaction.executeWithoutResult(status ->
                attempts.createIfAbsent(
                        decision,
                        new MandateId(approved.mandateId()),
                        approved.checkoutSessionId(),
                        clock.now().plus(properties.sessionLifetime()),
                        MerchantReference.parse(approved.merchantReference()),
                        approved.amount()));
        Instant credentialDeadline =
                clock.now().plus(properties.credentialTimeout()).plus(properties.commitBound());
        if (!leaveCreated(decision, new MandateId(approved.mandateId()), credentialDeadline)) {
            log.info(
                    "attempt for decision {} is past CREATED; this delivery changes nothing",
                    decision.value());
            return;
        }
        switch (credentials.request(decision, approved.amount())) {
            case CredentialIssuer.CredentialResult.Refused refused ->
                    fail(decision, AttemptState.CREDENTIAL_REQUESTED, refused.reasonCode());
            case CredentialIssuer.CredentialResult.NoAnswer ignored ->
                    unknown(decision, AttemptState.CREDENTIAL_REQUESTED);
            case CredentialIssuer.CredentialResult.Issued issued ->
                    completeWith(decision, approved, issued.credential());
        }
    }

    /**
     * CREATED → CREDENTIAL_REQUESTED, or CREATED → CANCELLED when the mandate was revoked between
     * the decision and this step (revocation cancels what has not asked for a credential,
     * ADR-0006). The mandate is share-locked in the same transaction, so a revocation either is
     * seen here or waits for this commit and then finds the attempt past CREATED. Returns whether
     * this caller moved the attempt to CREDENTIAL_REQUESTED.
     */
    private boolean leaveCreated(
            DecisionId decision, MandateId mandate, Instant credentialDeadline) {
        return Boolean.TRUE.equals(
                transaction.execute(status -> {
                    boolean revoked =
                            mandates.lockTermsOf(mandate)
                                    .map(terms -> terms.status() == MandateStatus.REVOKED)
                                    .orElse(true);
                    if (revoked) {
                        if (attempts.move(
                                decision,
                                AttemptState.CREATED,
                                AttemptState.CANCELLED,
                                Optional.empty(),
                                Optional.of("mandate-revoked"))) {
                            ledger.release(decision);
                            preApprovals.cancel(decision);
                        }
                        return false;
                    }
                    return attempts.move(
                            decision,
                            AttemptState.CREATED,
                            AttemptState.CREDENTIAL_REQUESTED,
                            Optional.of(credentialDeadline),
                            Optional.empty());
                }));
    }

    private void completeWith(DecisionId decision, DecisionMade approved, Credential credential) {
        Instant completionDeadline =
                clock.now().plus(properties.completionTimeout()).plus(properties.commitBound());
        if (!move(
                decision,
                AttemptState.CREDENTIAL_REQUESTED,
                AttemptState.COMPLETING,
                completionDeadline)) {
            log.info(
                    "attempt for decision {} left CREDENTIAL_REQUESTED under us; credential unused",
                    decision.value());
            return;
        }
        switch (checkout.complete(
                approved.checkoutSessionId(), credential, decision.value().toString())) {
            case Checkout.CompletionResult.Completed ignored ->
                    complete(decision, AttemptState.COMPLETING);
            case Checkout.CompletionResult.Refused refused ->
                    fail(decision, AttemptState.COMPLETING, refused.reasonCode());
            case Checkout.CompletionResult.AlreadyCompleted ignored ->
                    // evidence that the merchant did complete, by someone's call: never FAILED;
                    // the resolver confirms it through the session status
                    unknown(decision, AttemptState.COMPLETING);
            case Checkout.CompletionResult.NoAnswer ignored ->
                    unknown(decision, AttemptState.COMPLETING);
        }
    }

    /** The resolver's path for one attempt past its deadline or in UNKNOWN. */
    void resolve(CheckoutAttempt attempt) {
        switch (checkout.status(attempt.checkoutSessionId())) {
            case Checkout.SessionStatus.Completed ignored ->
                    complete(attempt.decisionId(), attempt.state());
            case Checkout.SessionStatus.NotFound ignored -> {
                // the merchant does not know the session: no evidence either way (§3), so UNKNOWN
                // until the pre-approval window closes; closing the window is T-003's job
                if (attempt.state() != AttemptState.UNKNOWN) {
                    unknown(attempt.decisionId(), attempt.state());
                }
            }
            case Checkout.SessionStatus.Open ignored -> {
                if (clock.now().isAfter(attempt.sessionExpiresAt())) {
                    fail(attempt.decisionId(), attempt.state(), "session-expired-uncompleted");
                } else if (attempt.state() != AttemptState.UNKNOWN) {
                    unknown(attempt.decisionId(), attempt.state());
                }
            }
            case Checkout.SessionStatus.NoAnswer ignored ->
                    log.info(
                            "merchant gave no answer for decision {}; resolving next time",
                            attempt.decisionId().value());
        }
    }

    /**
     * Revocation cancels what has not asked for a credential yet; later attempts run to their end.
     */
    void cancelCreatedFor(MandateId mandate) {
        for (CheckoutAttempt attempt : attempts.inState(AttemptState.CREATED, mandate)) {
            transaction.executeWithoutResult(status -> {
                if (attempts.move(
                        attempt.decisionId(),
                        AttemptState.CREATED,
                        AttemptState.CANCELLED,
                        Optional.empty(),
                        Optional.of("mandate-revoked"))) {
                    ledger.release(attempt.decisionId());
                    preApprovals.cancel(attempt.decisionId());
                }
            });
        }
    }

    private boolean move(
            DecisionId decision, AttemptState from, AttemptState to, Instant deadline) {
        return Boolean.TRUE.equals(
                transaction.execute(status ->
                        attempts.move(
                                decision, from, to, Optional.of(deadline), Optional.empty())));
    }

    private void complete(DecisionId decision, AttemptState from) {
        transaction.executeWithoutResult(status -> {
            if (attempts.move(
                    decision, from, AttemptState.COMPLETED, Optional.empty(), Optional.empty())) {
                ledger.count(decision);
                CheckoutAttempt attempt = attempts.find(decision).orElseThrow();
                events.publishEvent(
                        new CheckoutCompleted(
                                decision.value(),
                                attempt.id().value(),
                                attempt.mandateId().value()));
            }
        });
    }

    private void fail(DecisionId decision, AttemptState from, String reasonCode) {
        transaction.executeWithoutResult(status -> {
            if (attempts.move(
                    decision,
                    from,
                    AttemptState.FAILED,
                    Optional.empty(),
                    Optional.of(reasonCode))) {
                ledger.release(decision);
                preApprovals.cancel(decision);
                CheckoutAttempt attempt = attempts.find(decision).orElseThrow();
                events.publishEvent(
                        new CheckoutFailed(decision.value(), attempt.id().value(), reasonCode));
            }
        });
    }

    private void unknown(DecisionId decision, AttemptState from) {
        transaction.executeWithoutResult(status -> {
            if (attempts.move(
                    decision, from, AttemptState.UNKNOWN, Optional.empty(), Optional.empty())) {
                CheckoutAttempt attempt = attempts.find(decision).orElseThrow();
                events.publishEvent(
                        new CheckoutOutcomeUnknown(decision.value(), attempt.id().value()));
            }
        });
    }
}
