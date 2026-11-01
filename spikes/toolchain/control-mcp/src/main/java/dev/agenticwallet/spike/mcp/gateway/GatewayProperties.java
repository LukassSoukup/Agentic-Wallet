package dev.agenticwallet.spike.mcp.gateway;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The connector gateway hosted inside control-mcp behind its own listener (ADR-0008, T-037). Port 0
 * means an ephemeral listener, for tests that run several contexts in one JVM.
 */
@Validated
@ConfigurationProperties("skeleton.gateway")
record GatewayProperties(@Min(0) @Max(65535) int port) {}
