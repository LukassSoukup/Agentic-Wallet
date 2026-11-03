package dev.agenticwallet.spike.harness;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** What the scripted driver proposes; SYNTHETIC ids from the skeleton's fixtures. */
@Validated
@ConfigurationProperties("skeleton.purchase")
record ScriptedPurchaseProperties(
        @NotBlank String mandateId,
        @NotBlank String merchantReference,
        @NotBlank String merchantItemId,
        @Min(1) int quantity) {}
