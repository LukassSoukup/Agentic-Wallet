package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.UntrustedText;
import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantItemId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import dev.agenticwallet.spike.kernel.ids.PurchaseKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Object mother for decision inputs; every value is synthetic. */
final class DecisionInputs {

    static final Instant DECIDED_AT = Instant.parse("2026-11-01T10:15:30Z");
    static final MandateId MANDATE =
            new MandateId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));
    static final AgentInstanceId AGENT =
            new AgentInstanceId(UUID.fromString("00000000-0000-0000-0000-00000000b001"));
    static final MerchantReference DEMO_SHOP = MerchantReference.parse("demo-shop");

    private DecisionInputs() {}

    static Proposal proposal(PurchaseKey key) {
        return new Proposal(
                AGENT,
                key,
                MANDATE,
                DEMO_SHOP,
                List.of(new OrderLine(MerchantItemId.parse("SYN-0001"), 1)),
                UntrustedText.of("synthetic kettle"));
    }

    static MandateTerms activeMandate(String limit, String budget) {
        return new MandateTerms(
                MANDATE, 1, MandateStatus.ACTIVE, Money.of(limit, "EUR"), Money.of(budget, "EUR"));
    }

    static DecisionInput input(MandateTerms mandate, String cartTotal, LedgerSnapshot ledger) {
        return new DecisionInput(
                DecisionInput.SCHEMA_VERSION,
                Decider.RULE_SET_VERSION,
                proposal(PurchaseKey.parse("synthetic-key-1")),
                mandate,
                new CartEvidence(
                        "session-1", Money.of(cartTotal, "EUR"), DECIDED_AT.plusSeconds(600)),
                ledger,
                DECIDED_AT);
    }

    static LedgerSnapshot nothingUsed() {
        return new LedgerSnapshot(Money.of("0.00", "EUR"), Money.of("0.00", "EUR"));
    }

    static LedgerSnapshot used(String reserved, String counted) {
        return new LedgerSnapshot(Money.of(reserved, "EUR"), Money.of(counted, "EUR"));
    }
}
