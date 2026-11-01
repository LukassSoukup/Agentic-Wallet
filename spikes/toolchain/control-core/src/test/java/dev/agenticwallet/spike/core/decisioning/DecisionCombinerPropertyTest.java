package dev.agenticwallet.spike.core.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;

/**
 * The combiner only ever raises caution (invariant 2) and keeps every reason. jetCheck properties.
 */
class DecisionCombinerPropertyTest {

    private static final Generator<Verdict> VERDICTS =
            Generator.from(env -> {
                Outcome outcome = env.generate(Generator.sampledFrom(Outcome.values()));
                List<ReasonCode> reasons =
                        env.generate(
                                Generator.nonEmptyLists(
                                        Generator.sampledFrom(ReasonCode.values())));
                return new Verdict(outcome, reasons);
            });

    private static final Generator<List<Verdict>> VERDICT_LISTS = Generator.nonEmptyLists(VERDICTS);

    @Test
    void resultIsAtLeastAsCautiousAsEveryInput() {
        PropertyChecker.forAll(
                VERDICT_LISTS,
                verdicts -> {
                    Outcome combined = DecisionCombiner.combine(verdicts).outcome();
                    return verdicts.stream()
                            .allMatch(v -> combined.isAtLeastAsCautiousAs(v.outcome()));
                });
    }

    @Test
    void resultIsOneOfTheInputOutcomes() {
        PropertyChecker.forAll(
                VERDICT_LISTS,
                verdicts ->
                        verdicts.stream()
                                .map(Verdict::outcome)
                                .anyMatch(o -> o == DecisionCombiner.combine(verdicts).outcome()));
    }

    @Test
    void orderDoesNotChangeTheOutcome() {
        PropertyChecker.forAll(
                VERDICT_LISTS,
                verdicts -> {
                    List<Verdict> reversed = new ArrayList<>(verdicts);
                    Collections.reverse(reversed);
                    return DecisionCombiner.combine(reversed).outcome()
                            == DecisionCombiner.combine(verdicts).outcome();
                });
    }

    @Test
    void everyReasonSurvives() {
        PropertyChecker.forAll(
                VERDICT_LISTS,
                verdicts -> {
                    int expected = verdicts.stream().mapToInt(v -> v.reasons().size()).sum();
                    return DecisionCombiner.combine(verdicts).reasons().size() == expected;
                });
    }

    @Test
    void combinerIsPureOverTheWholeSample() {
        assertThat(
                        DecisionCombiner.combine(
                                        List.of(
                                                Verdict.of(
                                                        Outcome.APPROVE, ReasonCode.WITHIN_LIMIT)))
                                .outcome())
                .isEqualTo(Outcome.APPROVE);
    }
}
