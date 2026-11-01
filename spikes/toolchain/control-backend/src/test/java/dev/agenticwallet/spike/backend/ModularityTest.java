package dev.agenticwallet.spike.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith verifies the module boundaries; a cycle or an internal access fails the build.
 */
class ModularityTest {

    @Test
    void modulesAreWellFormed() {
        ApplicationModules modules = ApplicationModules.of(ControlBackendApplication.class);
        modules.verify();
    }
}
