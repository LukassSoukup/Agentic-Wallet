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
 * 06-consistency §4 in one transaction. First the processor's authorisation id is claimed in the
 * call log (PENDING): a twin delivery of the same id blocks on that key until this transaction ends
 * and then reads the stored answer. Then the first unconsumed pre-approval the shared rule matches
 * is consumed under the unique key ({@code ON CONFLICT DO NOTHING}: no row means another
 * authorisation got it first), and the claimed row is completed with the answer. Shadow mode
 * records exactly what enforcing mode would.
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
        if (!claim(authorisation, receivedAt)) {
            return storedAnswerFor(authorisation.processorAuthorisationId())
                    .orElseThrow(() ->
                            new ClaimedWithoutAnswerException(
                                    authorisation.processorAuthorisationId()));
        }
        HookAnswer answer = HookAnswer.noMatch(properties.mode());
        for (PreApproval candidate :
                preApprovals.unconsumedAt(
                        authorisation.merchant(), authorisation.amount().currency())) {
            if (PreApprovalMatchRule.matches(candidate, authorisation)
                    && consume(candidate, authorisation, receivedAt)) {
                answer = HookAnswer.match(candidate.decisionId(), properties.mode());
                break;
            }
        }
        complete(authorisation, answer);
        return answer;
    }

    /** True when this call owns the processor id; false when an earlier call (or a twin) does. */
    private boolean claim(Authorisation authorisation, Instant receivedAt) {
        int rows =
                jdbc.sql(
                                "insert into hook.call_log (processor_authorisation_id, answer,"
                                    + " amount, currency, merchant_reference, mode, received_at)"
                                    + " values (:id, 'PENDING', :amount, :currency, :merchant,"
                                    + " :mode, :at) on conflict (processor_authorisation_id) do"
                                    + " nothing")
                        .param("id", authorisation.processorAuthorisationId())
                        .param("amount", authorisation.amount().amount())
                        .param("currency", authorisation.amount().currency().getCurrencyCode())
                        .param("merchant", authorisation.merchant().value())
                        .param("mode", properties.mode().name())
                        .param("at", Timestamp.from(receivedAt))
                        .update();
        return rows == 1;
    }

    private Optional<HookAnswer> storedAnswerFor(String processorAuthorisationId) {
        return jdbc.sql(
                        "select answer, pre_approval_id from hook.call_log where"
                                + " processor_authorisation_id = :id and answer <> 'PENDING'")
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

    private void complete(Authorisation authorisation, HookAnswer answer) {
        jdbc.sql(
                        "update hook.call_log set answer = :answer, pre_approval_id = :pa where"
                                + " processor_authorisation_id = :id")
                .param("answer", answer.answer().name())
                .param("pa", answer.preApproval().map(DecisionId::value).orElse(null))
                .param("id", authorisation.processorAuthorisationId())
                .update();
    }

    /** A claimed row whose owner never completed it: a crash mid-call; the processor retries. */
    static final class ClaimedWithoutAnswerException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        ClaimedWithoutAnswerException(String processorAuthorisationId) {
            super(
                    "authorisation "
                            + processorAuthorisationId
                            + " is claimed but has no answer yet");
        }
    }
}
