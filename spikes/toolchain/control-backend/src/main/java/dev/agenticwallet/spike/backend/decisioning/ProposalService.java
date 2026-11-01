package dev.agenticwallet.spike.backend.decisioning;

import dev.agenticwallet.spike.backend.audit.AuditEntry;
import dev.agenticwallet.spike.backend.audit.AuditLog;
import dev.agenticwallet.spike.backend.authorisation.PreApprovals;
import dev.agenticwallet.spike.backend.identity.CallerIdentity;
import dev.agenticwallet.spike.backend.ledger.Ledger;
import dev.agenticwallet.spike.backend.mandates.Mandates;
import dev.agenticwallet.spike.backend.payments.Checkouts;
import dev.agenticwallet.spike.core.authorisation.PreApproval;
import dev.agenticwallet.spike.core.decisioning.CartEvidence;
import dev.agenticwallet.spike.core.decisioning.Decider;
import dev.agenticwallet.spike.core.decisioning.DecisionInput;
import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.LedgerSnapshot;
import dev.agenticwallet.spike.core.decisioning.MandateTerms;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.core.decisioning.Proposal;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.core.payments.Checkout;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The purchase flow of 04-purchase-flow steps 2 to 8 for the skeleton. The merchant call (opening
 * the session) happens before the transaction; then one transaction inserts the proposal under its
 * unique key, locks the budget period, share-locks the mandate version, decides, stores the
 * decision, reserves and pre-approves on APPROVE, writes the audit record with the complete input
 * and the outbox row.
 */
@Service
public class ProposalService {

    private final ProposalRepository proposals;
    private final Mandates mandates;
    private final Checkouts checkouts;
    private final Ledger ledger;
    private final PreApprovals preApprovals;
    private final AuditLog audit;
    private final DecisionStore decisions;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final DecisioningProperties properties;

    ProposalService(
            ProposalRepository proposals,
            Mandates mandates,
            Checkouts checkouts,
            Ledger ledger,
            PreApprovals preApprovals,
            AuditLog audit,
            DecisionStore decisions,
            Clock clock,
            ApplicationEventPublisher events,
            TransactionTemplate transaction,
            DecisioningProperties properties) {
        this.proposals = proposals;
        this.mandates = mandates;
        this.checkouts = checkouts;
        this.ledger = ledger;
        this.preApprovals = preApprovals;
        this.audit = audit;
        this.decisions = decisions;
        this.clock = clock;
        this.events = events;
        this.transaction = transaction;
        this.properties = properties;
    }

    public ProposalOutcome propose(CallerIdentity caller, Proposal proposal) {
        Optional<ProposalOutcome> seen = answerFromStored(proposal);
        if (seen.isPresent()) {
            return seen.get();
        }
        Optional<MandateTerms> mandate = mandates.termsOf(proposal.mandateId());
        if (mandate.isEmpty()) {
            return new ProposalOutcome.UnknownMandate();
        }
        return switch (checkouts.openCheckoutSession(proposal.merchant(), proposal.lines())) {
            case Checkout.OpenResult.Unavailable unavailable ->
                    new ProposalOutcome.MerchantUnavailable(unavailable.reasonCode());
            case Checkout.OpenResult.Opened opened ->
                    decideOnce(caller, proposal, mandate.get(), opened);
        };
    }

    private ProposalOutcome decideOnce(
            CallerIdentity caller,
            Proposal proposal,
            MandateTerms mandate,
            Checkout.OpenResult.Opened opened) {
        try {
            return transaction.execute(
                    status -> decideAndCommit(caller, proposal, mandate, opened));
        } catch (DuplicateKeyException raced) {
            // a twin delivery committed first; the duplicate session is left to expire (§5)
            return answerFromStored(proposal).orElseThrow(() -> new TwinVanishedException(raced));
        }
    }

    private Optional<ProposalOutcome> answerFromStored(Proposal proposal) {
        return proposals
                .find(proposal.agentInstance(), proposal.purchaseKey())
                .map(stored ->
                        stored.proposal().hasSameBindingFieldsAs(proposal)
                                ? new ProposalOutcome.Decided(
                                        stored.decisionId(), verdictOf(stored.decisionId()), true)
                                : new ProposalOutcome.PurchaseKeyConflict());
    }

    private Verdict verdictOf(DecisionId decisionId) {
        return decisions
                .find(decisionId)
                .map(DecisionRecord::verdict)
                .orElseThrow(() -> new DecisionMissingException(decisionId));
    }

    private ProposalOutcome decideAndCommit(
            CallerIdentity caller,
            Proposal proposal,
            MandateTerms mandate,
            Checkout.OpenResult.Opened opened) {
        Instant now = clock.now();
        DecisionId decisionId = new DecisionId(UUID.randomUUID());
        proposals.insert(proposal, decisionId, now);
        LocalDate period = periodOf(now);
        LedgerSnapshot snapshot = ledger.lockAndRead(mandate.id(), period, mandate.budget());
        // the version the decision is made under is the one share-locked here, not the copy read
        // before the merchant call: a revocation in between is seen, or waits for this commit
        MandateTerms locked =
                mandates.lockTermsOf(mandate.id())
                        .orElseThrow(() -> new MandateVanishedException(mandate.id()));
        CartEvidence cart =
                new CartEvidence(opened.sessionId(), opened.total(), opened.expiresAt());
        DecisionInput input =
                new DecisionInput(
                        DecisionInput.SCHEMA_VERSION,
                        Decider.RULE_SET_VERSION,
                        proposal,
                        locked,
                        cart,
                        snapshot,
                        now);
        Verdict verdict = Decider.decide(input);
        decisions.append(
                new DecisionRecord(
                        decisionId,
                        now,
                        locked.id(),
                        proposal.merchant(),
                        cart.checkoutSessionId(),
                        cart.total(),
                        verdict));
        if (verdict.outcome() == Outcome.APPROVE) {
            ledger.reserve(decisionId, locked.id(), period, cart.total());
            preApprovals.preApprove(
                    new PreApproval(
                            decisionId,
                            cart.total(),
                            proposal.merchant(),
                            now,
                            now.plus(properties.preApprovalWindow())));
        }
        audit.append(
                new AuditEntry(
                        decisionId, caller.cardholderId(), caller.agentInstance(), verdict, input));
        events.publishEvent(
                new DecisionMade(
                        decisionId.value(),
                        locked.id().value(),
                        verdict.outcome().name(),
                        cart.total(),
                        proposal.merchant().value(),
                        cart.checkoutSessionId()));
        return new ProposalOutcome.Decided(decisionId, verdict, false);
    }

    /** The budget period of the skeleton: the calendar month in UTC. */
    static LocalDate periodOf(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
    }

    static final class TwinVanishedException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        TwinVanishedException(Throwable cause) {
            super("the proposal that won the unique key is not readable", cause);
        }
    }

    static final class MandateVanishedException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        MandateVanishedException(MandateId id) {
            super("mandate " + id.value() + " disappeared between the read and the decision");
        }
    }

    static final class DecisionMissingException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        DecisionMissingException(DecisionId id) {
            super("proposal points at decision " + id.value() + " which is not stored");
        }
    }
}
