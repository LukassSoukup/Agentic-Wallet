package dev.agenticwallet.spike.simulators.processor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a scenario inject an authorisation at the SYNTHETIC processor directly (a second
 * authorisation against one pre-approval, a replayed id) and read the hook's answer.
 */
@RestController
@RequestMapping("/processor/authorisations")
class ProcessorController {

    private final HookResponderClient hook;

    ProcessorController(HookResponderClient hook) {
        this.hook = hook;
    }

    @PostMapping
    HookResponderClient.HookAnswer authorise(@RequestBody HookResponderClient.HookRequest request) {
        return hook.ask(request);
    }
}
