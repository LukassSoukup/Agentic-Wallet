package dev.agenticwallet.spike.backend.authorisation;

import dev.agenticwallet.spike.core.authorisation.PreApproval;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import dev.agenticwallet.spike.kernel.ids.MerchantReference;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Published API of {@code authorisation}: the pre-approval the hook matches against, written in the
 * decision's transaction and cancelled in the attempt's ({@code MANDATORY}). The hook responder
 * reads the published view, never this table.
 */
@Component
public class PreApprovals {

    private final JdbcClient jdbc;

    PreApprovals(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void preApprove(PreApproval preApproval) {
        jdbc.sql(
                        "insert into authorisation.pre_approval (decision_id, amount, currency,"
                                + " merchant_reference, valid_from, valid_until, state) values"
                                + " (:decision, :amount, :currency, :merchant, :from, :until,"
                                + " 'ACTIVE')")
                .param("decision", preApproval.decisionId().value())
                .param("amount", preApproval.amount().amount())
                .param("currency", preApproval.amount().currency().getCurrencyCode())
                .param("merchant", preApproval.merchant().value())
                .param("from", Timestamp.from(preApproval.validFrom()))
                .param("until", Timestamp.from(preApproval.validUntil()))
                .update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(DecisionId decision) {
        jdbc.sql(
                        "update authorisation.pre_approval set state = 'CANCELLED' where"
                                + " decision_id = :decision")
                .param("decision", decision.value())
                .update();
    }

    public Optional<PreApproval> findActive(DecisionId decision) {
        return jdbc.sql(
                        "select decision_id, amount, currency, merchant_reference, valid_from,"
                                + " valid_until from authorisation.pre_approval_published where"
                                + " decision_id = :decision")
                .param("decision", decision.value())
                .query((row, n) ->
                        new PreApproval(
                                new DecisionId(row.getObject("decision_id", java.util.UUID.class)),
                                new Money(
                                        row.getBigDecimal("amount"),
                                        Currency.getInstance(row.getString("currency"))),
                                MerchantReference.parse(row.getString("merchant_reference")),
                                row.getTimestamp("valid_from").toInstant(),
                                row.getTimestamp("valid_until").toInstant()))
                .optional();
    }
}
