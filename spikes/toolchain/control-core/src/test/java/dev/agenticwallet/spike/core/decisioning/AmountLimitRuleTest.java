package dev.agenticwallet.spike.core.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agenticwallet.spike.kernel.Money;
import org.junit.jupiter.api.Test;

class AmountLimitRuleTest {

    private final AmountLimitRule rule = new AmountLimitRule(Money.of("100.00", "EUR"));

    @Test
    void approvesAtTheLimit() {
        Verdict verdict = rule.judge(Money.of("100.00", "EUR"));
        assertThat(verdict.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(verdict.reasons()).containsExactly(ReasonCode.WITHIN_LIMIT);
    }

    @Test
    void declinesOneCentOver() {
        Verdict verdict = rule.judge(Money.of("100.01", "EUR"));
        assertThat(verdict.outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(verdict.reasons()).containsExactly(ReasonCode.OVER_LIMIT);
    }
}
