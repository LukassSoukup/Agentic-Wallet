package dev.agenticwallet.spike.backend.decisioning;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The pre-approval window after an APPROVE (05-open-items: a settings value in the real system,
 * seven days as the stand-in). Configuration in the skeleton; T-003 moves it to {@code settings}.
 */
@Validated
@ConfigurationProperties("skeleton.decisioning")
record DecisioningProperties(@NotNull Duration preApprovalWindow) {}
