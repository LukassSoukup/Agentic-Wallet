package dev.agenticwallet.spike.backend.skeleton;

import dev.agenticwallet.spike.core.decisioning.Proposal;
import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.UntrustedText;
import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantItemId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import dev.agenticwallet.spike.kernel.ids.PurchaseKey;
import java.util.List;
import java.util.UUID;

/** Object mother for the scenarios; SYNTHETIC ids that match the migration's seed. */
final class Proposals {

    static final MandateId SEEDED_MANDATE =
            new MandateId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));
    static final AgentInstanceId AGENT =
            new AgentInstanceId(UUID.fromString("00000000-0000-0000-0000-00000000b001"));
    static final UUID CARDHOLDER = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    static final MerchantReference DEMO_SHOP = MerchantReference.parse("demo-shop");

    /** SYN-0001 costs 29.90 EUR at the demo shop; SYN-0002 costs 89.00 EUR. */
    static final MerchantItemId KETTLE = MerchantItemId.parse("SYN-0001");

    static final MerchantItemId FLIGHT = MerchantItemId.parse("SYN-0002");

    private Proposals() {}

    static Proposal of(MandateId mandate, MerchantItemId item, int quantity, String description) {
        return new Proposal(
                AGENT,
                PurchaseKey.parse("key-" + UUID.randomUUID()),
                mandate,
                DEMO_SHOP,
                List.of(new OrderLine(item, quantity)),
                UntrustedText.of(description));
    }

    static Proposal withKey(Proposal proposal, PurchaseKey key) {
        return new Proposal(
                proposal.agentInstance(),
                key,
                proposal.mandateId(),
                proposal.merchant(),
                proposal.lines(),
                proposal.description());
    }

    static Proposal reworded(Proposal proposal, String description) {
        return new Proposal(
                proposal.agentInstance(),
                proposal.purchaseKey(),
                proposal.mandateId(),
                proposal.merchant(),
                proposal.lines(),
                UntrustedText.of(description));
    }
}
