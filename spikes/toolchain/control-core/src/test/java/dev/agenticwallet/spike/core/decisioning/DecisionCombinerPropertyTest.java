package dev.agenticwallet.spike.core.decisioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** The combiner only ever raises caution (invariant 2) and keeps every reason. */
class DecisionCombinerPropertyTest {

    @Provide
    Arbitrary<List<Verdict>> verdictLists() {
        Arbitrary<Verdict> verdicts =
                Arbitraries.of(Outcome.class)
                        .flatMap(outcome ->
                                Arbitraries.of(ReasonCode.class)
                                        .list()
                                        .ofMinSize(1)
                                        .ofMaxSize(3)
                                        .map(reasons -> new Verdict(outcome, reasons)));
        return verdicts.list().ofMinSize(1).ofMaxSize(6);
    }

    @Property
    void resultIsAtLeastAsCautiousAsEveryInput(@ForAll("verdictLists") List<Verdict> verdicts) {
        Verdict combined = DecisionCombiner.combine(verdicts);
        assertThat(verdicts)
                .allSatisfy(v ->
                        assertThat(combined.outcome().isAtLeastAsCautiousAs(v.outcome())).isTrue());
    }

    @Property
    void resultIsOneOfTheInputOutcomes(@ForAll("verdictLists") List<Verdict> verdicts) {
        Verdict combined = DecisionCombiner.combine(verdicts);
        assertThat(verdicts).extracting(Verdict::outcome).contains(combined.outcome());
    }

    @Property
    void orderDoesNotChangeTheOutcome(@ForAll("verdictLists") List<Verdict> verdicts) {
        List<Verdict> reversed = new ArrayList<>(verdicts);
        Collections.reverse(reversed);
        assertThat(DecisionCombiner.combine(reversed).outcome())
                .isEqualTo(DecisionCombiner.combine(verdicts).outcome());
    }

    @Property
    void everyReasonSurvives(@ForAll("verdictLists") List<Verdict> verdicts) {
        Verdict combined = DecisionCombiner.combine(verdicts);
        int expected = verdicts.stream().mapToInt(v -> v.reasons().size()).sum();
        assertThat(combined.reasons()).hasSize(expected);
    }
}
