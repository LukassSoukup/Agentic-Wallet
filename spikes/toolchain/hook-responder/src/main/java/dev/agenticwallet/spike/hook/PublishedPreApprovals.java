package dev.agenticwallet.spike.hook;

import dev.agenticwallet.spike.core.authorisation.PreApproval;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads the backend's published view with plain SQL on the hook's own small pool (ADR-0009 §A): the
 * active pre-approvals at a merchant in a currency that no authorisation has consumed yet.
 */
@Component
class PublishedPreApprovals {

    private final JdbcClient jdbc;

    PublishedPreApprovals(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<PreApproval> unconsumedAt(MerchantReference merchant, Currency currency) {
        return jdbc.sql(
                        "select p.decision_id, p.amount, p.currency, p.merchant_reference,"
                                + " p.valid_from, p.valid_until from"
                                + " authorisation.pre_approval_published p left join"
                                + " hook.pre_approval_consumption c on c.pre_approval_id ="
                                + " p.decision_id where c.pre_approval_id is null and"
                                + " p.merchant_reference = :merchant and p.currency = :currency"
                                + " order by p.valid_from")
                .param("merchant", merchant.value())
                .param("currency", currency.getCurrencyCode())
                .query((row, n) ->
                        new PreApproval(
                                new DecisionId(row.getObject("decision_id", UUID.class)),
                                new Money(
                                        row.getBigDecimal("amount"),
                                        Currency.getInstance(row.getString("currency"))),
                                MerchantReference.parse(row.getString("merchant_reference")),
                                row.getTimestamp("valid_from").toInstant(),
                                row.getTimestamp("valid_until").toInstant()))
                .list();
    }
}
