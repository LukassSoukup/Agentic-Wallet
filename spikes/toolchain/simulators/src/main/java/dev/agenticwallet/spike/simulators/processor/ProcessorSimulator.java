package dev.agenticwallet.spike.simulators.processor;

import dev.agenticwallet.spike.simulators.demoshop.CheckoutSession;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * The bank processor, SYNTHETIC: every authorisation the merchant's payment provider raises is sent
 * to the hook responder with the processor's authorisation id. The answer is logged as a code; in
 * shadow mode nothing else happens with it.
 */
@Component
public class ProcessorSimulator {

    private static final Logger log = LoggerFactory.getLogger(ProcessorSimulator.class);

    private final HookResponderClient hook;

    ProcessorSimulator(HookResponderClient hook) {
        this.hook = hook;
    }

    /**
     * In shadow mode the hook's answer changes nothing at the processor, so a hook that is down or
     * late is recorded as such and the authorisation goes through: the merchant's checkout never
     * depends on our responder. This is the processor's fallback, not a swallowed failure of ours.
     */
    public void authorise(CheckoutSession session, String authorisationId) {
        try {
            HookResponderClient.HookAnswer answer =
                    hook.ask(
                            new HookResponderClient.HookRequest(
                                    authorisationId,
                                    session.total(),
                                    "EUR",
                                    session.merchantReference(),
                                    Instant.now()));
            log.info(
                    "processor authorisation {} answered {} by the hook",
                    authorisationId,
                    answer.answer());
        } catch (RestClientException hookUnavailable) {
            log.warn(
                    "processor authorisation {}: hook gave no usable answer ({}); fallback applied",
                    authorisationId,
                    hookUnavailable.getClass().getSimpleName());
        }
    }
}
