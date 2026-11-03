package dev.agenticwallet.spike.backend.skeleton;

import dev.agenticwallet.spike.kernel.events.CheckoutCompleted;
import dev.agenticwallet.spike.kernel.events.CheckoutFailed;
import dev.agenticwallet.spike.kernel.events.CheckoutOutcomeUnknown;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * The scenarios' stand-in for {@code activity} and {@code notifications}: records which checkout
 * events reached a listener. Spring Modulith writes a publication row only for events that have a
 * listener, so without this class an event nobody consumes yet leaves no durable trace at all. The
 * bean is proxied for the listener annotation, so state is read through methods (T-008 finding).
 */
public class RecordedCheckoutEvents {

    private final List<UUID> unknown = new CopyOnWriteArrayList<>();
    private final List<UUID> completed = new CopyOnWriteArrayList<>();
    private final List<UUID> failed = new CopyOnWriteArrayList<>();

    @ApplicationModuleListener
    void on(CheckoutOutcomeUnknown event) {
        unknown.add(event.decisionId());
    }

    @ApplicationModuleListener
    void on(CheckoutCompleted event) {
        completed.add(event.decisionId());
    }

    @ApplicationModuleListener
    void on(CheckoutFailed event) {
        failed.add(event.decisionId());
    }

    public List<UUID> unknownDecisions() {
        return List.copyOf(unknown);
    }

    public List<UUID> completedDecisions() {
        return List.copyOf(completed);
    }

    public List<UUID> failedDecisions() {
        return List.copyOf(failed);
    }
}
