package dev.agenticwallet.spike.backend.decisioning;

import dev.agenticwallet.spike.core.decisioning.AmountLimitRule;
import dev.agenticwallet.spike.core.decisioning.DecisionCombiner;
import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.Verdict;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service: runs the pure rules, stores the decision and publishes the event in one
 * transaction; the event reaches other modules through the outbox after commit.
 */
@Service
public class DecisionService {

    private final DecisionStore store;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final AmountLimitRule limitRule;

    DecisionService(DecisionStore store, Clock clock, ApplicationEventPublisher events) {
        this.store = store;
        this.clock = clock;
        this.events = events;
        this.limitRule = new AmountLimitRule(Money.of("100.00", "EUR"));
    }

    @Transactional
    public DecisionRecord decide(UUID decisionId, Money amountInBaseCurrency) {
        Verdict verdict = DecisionCombiner.combine(List.of(limitRule.judge(amountInBaseCurrency)));
        DecisionRecord record =
                new DecisionRecord(decisionId, clock.now(), amountInBaseCurrency, verdict);
        store.append(record);
        events.publishEvent(new DecisionMade(decisionId, verdict.outcome().name()));
        return record;
    }
}
