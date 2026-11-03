package dev.agenticwallet.spike.mcp.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two listeners, two surfaces: the gateway endpoint answers only on the gateway port and the
 * control endpoint only on the main port, so the harness fence (ADR-0006) can name two addresses
 * that are two different servers even while one process hosts both.
 */
@Component
class ListenerSeparation extends OncePerRequestFilter {

    private final int gatewayPort;

    ListenerSeparation(GatewayProperties properties) {
        this.gatewayPort = properties.port();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean gatewayPath = request.getRequestURI().startsWith(HostedConnectorGateway.ENDPOINT);
        boolean gatewayListener = request.getLocalPort() == gatewayPort;
        if (gatewayPath != gatewayListener && !request.getRequestURI().startsWith("/actuator")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
