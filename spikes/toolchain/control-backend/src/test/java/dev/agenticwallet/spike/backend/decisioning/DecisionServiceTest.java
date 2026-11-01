package dev.agenticwallet.spike.backend.decisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.agenticwallet.spike.core.decisioning.DecisionRecord;
import dev.agenticwallet.spike.core.decisioning.DecisionStore;
import dev.agenticwallet.spike.core.decisioning.Outcome;
import dev.agenticwallet.spike.kernel.Clock;
import dev.agenticwallet.spike.kernel.Money;
import dev.agenticwallet.spike.kernel.events.DecisionMade;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Mockito on Java 25 (ByteBuddy agent attached by surefire's argLine); ports mocked, core not. */
@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    private static final Instant FIXED = Instant.parse("2026-11-01T00:00:00Z");

    @Mock DecisionStore store;
    @Mock Clock clock;
    @Mock ApplicationEventPublisher events;

    @Test
    void storesTheDecisionAndPublishesTheEventWithTheClocksTime() {
        when(clock.now()).thenReturn(FIXED);
        DecisionService service = new DecisionService(store, clock, events);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        DecisionRecord record = service.decide(id, Money.of("100.01", "EUR"));

        assertThat(record.verdict().outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(record.decidedAt()).isEqualTo(FIXED);
        verify(store).append(record);
        verify(events).publishEvent(any(DecisionMade.class));
    }
}
