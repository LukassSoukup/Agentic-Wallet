package dev.agenticwallet.spike.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces Spring AI's autoconfigured streamable transport (it yields to a bean of this type) with
 * one that extracts the agent session header; everything else keeps the configured properties.
 * Primary, because the hosted connector gateway adds a second provider that Spring AI must not
 * pick.
 */
@Configuration(proxyBeanMethods = false)
class ControlTransportConfiguration {

    @Bean
    @Primary
    WebMvcStreamableServerTransportProvider controlTransport(
            JsonMapper json, McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(json))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(new AgentSessionContext())
                .build();
    }
}
