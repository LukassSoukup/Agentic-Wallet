package dev.agenticwallet.spike.simulators.demoshop;

import java.time.Duration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Scenario control of the SYNTHETIC demo shop; a simulator-only surface. */
@RestController
@RequestMapping("/control")
class DemoShopControlController {

    private final DemoShopFaults faults;

    DemoShopControlController(DemoShopFaults faults) {
        this.faults = faults;
    }

    record DelayRequest(long completionDelayMillis) {}

    @PostMapping("/completion-delay")
    void delayCompletions(@RequestBody DelayRequest request) {
        faults.delayCompletionsBy(Duration.ofMillis(request.completionDelayMillis()));
    }

    @PostMapping("/reset")
    void reset() {
        faults.reset();
    }
}
