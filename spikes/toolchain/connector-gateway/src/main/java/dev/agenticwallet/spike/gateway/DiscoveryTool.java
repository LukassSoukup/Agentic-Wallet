package dev.agenticwallet.spike.gateway;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One discovery-class tool served by the gateway (ADR-0008); synthetic content. */
@Configuration(proxyBeanMethods = false)
class DiscoveryTool {

    static final String NAME = "search_catalogue";

    @Bean
    List<McpServerFeatures.SyncToolSpecification> discoveryTools() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder(
                                NAME,
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of("query", Map.of("type", "string")),
                                        "required",
                                        List.of("query")))
                        .description("Searches an admitted merchant's catalogue (synthetic).")
                        .build();
        return List.of(
                McpServerFeatures.SyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler((exchange, request) ->
                                McpSchema.CallToolResult.builder()
                                        .structuredContent(
                                                Map.of(
                                                        "items",
                                                        List.of(
                                                                Map.of(
                                                                        "merchantItemId",
                                                                        "SYN-0001")),
                                                        "synthetic",
                                                        true))
                                        .addTextContent("synthetic catalogue result")
                                        .build())
                        .build());
    }
}
