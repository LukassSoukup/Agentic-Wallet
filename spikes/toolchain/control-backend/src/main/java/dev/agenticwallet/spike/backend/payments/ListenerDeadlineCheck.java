package dev.agenticwallet.spike.backend.payments;

import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADR-0009 B6: start-up validates that the staleness monitor's processing bound exceeds the largest
 * listener deadline, so the monitor can never mark a listener failed while its own bound still
 * allows it to run. Fails start-up with a named error otherwise (B5).
 */
@Component
class ListenerDeadlineCheck implements InitializingBean {

    private final PaymentsProperties properties;
    private final Duration stalenessProcessing;
    private final Duration poolAcquisition;
    private final Duration transactionTimeout;

    ListenerDeadlineCheck(
            PaymentsProperties properties,
            @Value("${spring.modulith.events.staleness.processing}") Duration stalenessProcessing,
            @Value("${spring.datasource.hikari.connection-timeout}") long poolAcquisitionMillis,
            @Value("${skeleton.database.transaction-timeout}") Duration transactionTimeout) {
        this.properties = properties;
        this.stalenessProcessing = stalenessProcessing;
        this.poolAcquisition = Duration.ofMillis(poolAcquisitionMillis);
        this.transactionTimeout = transactionTimeout;
    }

    @Override
    public void afterPropertiesSet() {
        Duration deadline = properties.listenerDeadline(poolAcquisition, transactionTimeout);
        if (stalenessProcessing.compareTo(deadline) <= 0) {
            throw new StalenessBoundTooShortException(stalenessProcessing, deadline);
        }
    }

    static final class StalenessBoundTooShortException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        StalenessBoundTooShortException(Duration staleness, Duration deadline) {
            super(
                    "spring.modulith.events.staleness.processing ("
                            + staleness
                            + ") must exceed the checkout listener's deadline ("
                            + deadline
                            + ")");
        }
    }
}
