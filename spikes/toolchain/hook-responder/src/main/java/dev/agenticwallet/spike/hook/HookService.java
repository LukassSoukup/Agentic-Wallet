package dev.agenticwallet.spike.hook;

import dev.agenticwallet.spike.core.authorisation.Authorisation;
import dev.agenticwallet.spike.core.authorisation.PreApproval;
import dev.agenticwallet.spike.core.authorisation.PreApprovalMatchRule;
import dev.agenticwallet.spike.kernel.ids.DecisionId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 06-consistency §4 in one transaction: deduplicate on the processor's authorisation id, find the
 * first unconsumed pre-approval the shared rule matches, consume it under the unique key (an {@code
 * ON CONFLICT DO NOTHING} that returns no row means another authorisation got it first), write the
 * call log, answer. Shadow mode records exactly what enforcing mode would.
 */
@Service
class HookService {

    private final PublishedPreApprovals preApprovals;
    private final JdbcClient jdbc;
    private final HookProperties properties;

    HookService(PublishedPreApprovals preApprovals, JdbcClient jdbc, HookProperties properties) {
        this.preApprovals = preApprovals;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    HookAnswer answer(Authorisation authorisation, Instant receivedAt) {
        Optional<HookAnswer> stored = storedAnswerFor(authorisation.processorAuthorisationId());
        if (stored.isPresent()) {
            return stored.get();
        }
        HookAnswer answer =
                preApprovals
                        .unconsumedAt(authorisation.merchant(), authorisation.amount().currency())
                        .stream()
                        .filter(candidate -> PreApprovalMatchRule.matches(candidate, authorisation))
                        .filter(candidate -> consume(candidate, authorisation, receivedAt))
                        .findFirst()
                        .map(consumed -> HookAnswer.match(consumed.decisionId(), properties.mode()))
                        .orElseGet(() -> HookAnswer.noMatch(properties.mode()));
        logCall(authorisation, answer, receivedAt);
        return answer;
    }

    private Optional<HookAnswer> storedAnswerFor(String processorAuthorisationId) {
        return jdbc.sql(
                        "select answer, pre_approval_id from hook.call_log where"
                                + " processor_authorisation_id = :id")
                .param("id", processorAuthorisationId)
                .query((row, n) -> {
                    UUID preApproval = row.getObject("pre_approval_id", UUID.class);
                    return new HookAnswer(
                            HookAnswer.Verdict.valueOf(row.getString("answer")),
                            Optional.ofNullable(preApproval).map(DecisionId::new),
                            properties.mode());
                })
                .optional();
    }

    /**
     * True when this authorisation consumed the pre-approval; false when another one already had.
     */
    private boolean consume(
            PreApproval candidate, Authorisation authorisation, Instant receivedAt) {
        int rows =
                jdbc.sql(
                                "insert into hook.pre_approval_consumption (pre_approval_id,"
                                        + " processor_authorisation_id, consumed_at) values (:pa,"
                                        + " :auth, :at) on conflict (pre_approval_id) do nothing")
                        .param("pa", candidate.decisionId().value())
                        .param("auth", authorisation.processorAuthorisationId())
                        .param("at", Timestamp.from(receivedAt))
                        .update();
        return rows == 1;
    }

    private void logCall(Authorisation authorisation, HookAnswer answer, Instant receivedAt) {
        jdbc.sql(
                        "insert into hook.call_log (processor_authorisation_id, answer,"
                                + " pre_approval_id, amount, currency, merchant_reference, mode,"
                                + " received_at) values (:id, :answer, :pa, :amount, :currency,"
                                + " :merchant, :mode, :at)")
                .param("id", authorisation.processorAuthorisationId())
                .param("answer", answer.answer().name())
                .param("pa", answer.preApproval().map(DecisionId::value).orElse(null))
                .param("amount", authorisation.amount().amount())
                .param("currency", authorisation.amount().currency().getCurrencyCode())
                .param("merchant", authorisation.merchant().value())
                .param("mode", answer.mode().name())
                .param("at", Timestamp.from(receivedAt))
                .update();
    }
}
