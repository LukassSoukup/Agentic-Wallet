package dev.agenticwallet.spike.backend.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * ADR-0009 B6: the listener deadline is a true bound, and start-up refuses a shorter staleness
 * bound.
 */
class ListenerDeadlineCheckTest {

    private final PaymentsProperties properties =
            new PaymentsProperties(
                    "http://demo",
                    "http://network",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2),
                    Duration.ofMinutes(15));

    @Test
    void deadlineIsThreeTransactionsPlusBothCallsPlusTheMargin() {
        Duration deadline =
                properties.listenerDeadline(Duration.ofSeconds(5), Duration.ofSeconds(10));
        assertThat(deadline).isEqualTo(Duration.ofSeconds(3 * 15 + 3 + 5 + 2));
    }

    @Test
    void refusesAStalenessBoundAtOrBelowTheDeadlineAndAcceptsOneAbove() {
        assertThatThrownBy(() ->
                        new ListenerDeadlineCheck(
                                        properties,
                                        Duration.ofSeconds(55),
                                        5000,
                                        Duration.ofSeconds(10))
                                .afterPropertiesSet())
                .isInstanceOf(ListenerDeadlineCheck.StalenessBoundTooShortException.class);
        new ListenerDeadlineCheck(properties, Duration.ofSeconds(56), 5000, Duration.ofSeconds(10))
                .afterPropertiesSet();
    }
}
