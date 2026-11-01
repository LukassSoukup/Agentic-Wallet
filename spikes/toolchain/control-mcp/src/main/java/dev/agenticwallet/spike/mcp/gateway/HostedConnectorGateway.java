package dev.agenticwallet.spike.mcp.gateway;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.apache.catalina.connector.Connector;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * The connector gateway's MCP server, built by hand so Spring AI's autoconfiguration does not fold
 * its tools into the control server: its own transport at {@code /connector/mcp}, its own tool
 * list, reachable on a second Tomcat connector. T-037 measures this process against the separate
 * JVM of T-008. The tool content is SYNTHETIC.
 */
@Configuration(proxyBeanMethods = false)
class HostedConnectorGateway {

    static final String ENDPOINT = "/connector/mcp";
    static final String DISCOVERY_TOOL = "search_catalogue";

    @Bean
    WebMvcStreamableServerTransportProvider gatewayTransport(JsonMapper json) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .mcpEndpoint(ENDPOINT)
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> gatewayRouterFunction(
            @Qualifier("gatewayTransport") WebMvcStreamableServerTransportProvider gatewayTransport) {
        return gatewayTransport.getRouterFunction();
    }

    /**
     * Neither a {@code List<SyncToolSpecification>} bean nor a {@code McpSyncServer} bean on
     * purpose: the first would be folded into the control server, the second would make Spring AI's
     * own server ambiguous for every injection point.
     */
    @Bean(destroyMethod = "close")
    HostedGateway gatewayServer(
            @Qualifier("gatewayTransport") WebMvcStreamableServerTransportProvider gatewayTransport) {
        return new HostedGateway(
                McpServer.sync(gatewayTransport)
                        .serverInfo("connector-gateway", "0.0.1")
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                        .tools(List.of(discoveryTool()))
                        .build());
    }

    /**
     * Holds the gateway's server so it lives and dies with the context without being "the" server.
     */
    record HostedGateway(McpSyncServer server) implements AutoCloseable {
        @Override
        public void close() {
            server.closeGracefully();
        }
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> gatewayListener(
            GatewayProperties properties) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(properties.port());
            factory.addAdditionalConnectors(connector);
        };
    }

    private static McpServerFeatures.SyncToolSpecification discoveryTool() {
        McpSchema.Tool tool =
                McpSchema.Tool.builder(
                                DISCOVERY_TOOL,
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of("query", Map.of("type", "string")),
                                        "required", List.of("query")))
                        .description("Searches an admitted merchant's catalogue (synthetic).")
                        .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) ->
                        McpSchema.CallToolResult.builder()
                                .structuredContent(
                                        Map.of(
                                                "items",
                                                List.of(Map.of("merchantItemId", "SYN-0001")),
                                                "synthetic",
                                                true))
                                .addTextContent("synthetic catalogue result")
                                .build())
                .build();
    }
}
