package dev.agenticwallet.spike.mcp;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Where the backend is and how long a forwarded call may take (ADR-0009 B5, B6). */
@Validated
@ConfigurationProperties("skeleton.mcp")
record McpProperties(
        @NotNull String backendUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {}
