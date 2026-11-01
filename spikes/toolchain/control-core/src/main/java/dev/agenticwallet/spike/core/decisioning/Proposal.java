package dev.agenticwallet.spike.core.decisioning;

import dev.agenticwallet.spike.kernel.OrderLine;
import dev.agenticwallet.spike.kernel.UntrustedText;
import dev.agenticwallet.spike.kernel.ids.AgentInstanceId;
import dev.agenticwallet.spike.kernel.ids.MandateId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import dev.agenticwallet.spike.kernel.ids.PurchaseKey;
import java.util.List;

/**
 * What an agent asks the control layer to approve (glossary: purchase proposal). The agent instance
 * comes from the verified credential, never from the body. The binding fields (mandate, merchant,
 * lines) decide whether a redelivery is the same request (06-consistency §5); the description is
 * untrusted text and is never compared.
 */
public record Proposal(
        AgentInstanceId agentInstance,
        PurchaseKey purchaseKey,
        MandateId mandateId,
        MerchantReference merchant,
        List<OrderLine> lines,
        UntrustedText description) {

    public Proposal {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a proposal has at least one line");
        }
        lines = List.copyOf(lines);
    }

    public boolean hasSameBindingFieldsAs(Proposal other) {
        return mandateId.equals(other.mandateId)
                && merchant.equals(other.merchant)
                && lines.equals(other.lines);
    }
}
