package dev.agenticwallet.spike.core.payments;

import static dev.agenticwallet.spike.core.payments.AttemptState.CANCELLED;
import static dev.agenticwallet.spike.core.payments.AttemptState.COMPLETED;
import static dev.agenticwallet.spike.core.payments.AttemptState.COMPLETING;
import static dev.agenticwallet.spike.core.payments.AttemptState.CREATED;
import static dev.agenticwallet.spike.core.payments.AttemptState.CREDENTIAL_REQUESTED;
import static dev.agenticwallet.spike.core.payments.AttemptState.FAILED;
import static dev.agenticwallet.spike.core.payments.AttemptState.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** The transition table of 06-consistency §3, exactly. */
class AttemptStateTest {

    @Test
    void allowsExactlyTheDesignedTransitions() {
        assertThat(allowedFrom(CREATED)).containsExactlyInAnyOrder(CREDENTIAL_REQUESTED, CANCELLED);
        assertThat(allowedFrom(CREDENTIAL_REQUESTED))
                .containsExactlyInAnyOrder(COMPLETING, FAILED, UNKNOWN);
        assertThat(allowedFrom(COMPLETING)).containsExactlyInAnyOrder(COMPLETED, FAILED, UNKNOWN);
        assertThat(allowedFrom(UNKNOWN)).containsExactlyInAnyOrder(COMPLETED, FAILED);
    }

    @Test
    void terminalStatesMoveNowhereAndCancellationNeedsNoCredentialYet() {
        assertThat(COMPLETED.isTerminal()).isTrue();
        assertThat(FAILED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
        assertThat(CREDENTIAL_REQUESTED.canMoveTo(CANCELLED)).isFalse();
        assertThat(COMPLETING.canMoveTo(CANCELLED)).isFalse();
        assertThat(CREATED.canMoveTo(CREATED)).isFalse();
    }

    private static EnumSet<AttemptState> allowedFrom(AttemptState from) {
        EnumSet<AttemptState> allowed = EnumSet.noneOf(AttemptState.class);
        for (AttemptState to : AttemptState.values()) {
            if (from.canMoveTo(to)) {
                allowed.add(to);
            }
        }
        return allowed;
    }
}
