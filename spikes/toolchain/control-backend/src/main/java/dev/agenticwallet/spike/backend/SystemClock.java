package dev.agenticwallet.spike.backend;

import dev.agenticwallet.spike.kernel.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** The one place the backend reads the host clock; the core gets it through the port. */
@Component
class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
