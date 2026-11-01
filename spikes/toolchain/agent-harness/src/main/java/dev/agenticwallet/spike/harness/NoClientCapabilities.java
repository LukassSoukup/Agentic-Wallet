package dev.agenticwallet.spike.harness;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.stereotype.Component;

/**
 * ADR-0009 §A: the harness's clients advertise no sampling, elicitation or roots. Spring AI applies
 * this customizer to every autoconfigured client after its own defaults.
 */
@Component
class NoClientCapabilities implements McpClientCustomizer<McpClient.SyncSpec> {

    @Override
    public void customize(String name, McpClient.SyncSpec spec) {
        spec.capabilities(McpSchema.ClientCapabilities.builder().build());
        spec.clientInfo(McpSchema.Implementation.builder("agent-harness", "0.0.1").build());
    }
}
