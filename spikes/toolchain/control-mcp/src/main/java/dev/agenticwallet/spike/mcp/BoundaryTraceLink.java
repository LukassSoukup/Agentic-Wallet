package dev.agenticwallet.spike.mcp;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * ADR-0009 B4: at the trust boundary a new trace starts and the incoming one is attached as a span
 * link. The new trace comes from {@code management.tracing.propagation.consume} being empty, so
 * Boot's propagator ignores the harness's {@code traceparent}; this handler reads the raw header
 * and adds the link to the server span the moment its scope opens.
 */
@Component
class BoundaryTraceLink implements ObservationHandler<ServerRequestObservationContext> {

    static final String LINKED_ATTRIBUTE = BoundaryTraceLink.class.getName() + ".linked";

    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onScopeOpened(ServerRequestObservationContext context) {
        var request = context.getCarrier();
        if (request.getAttribute(LINKED_ATTRIBUTE) != null) {
            return;
        }
        String header = request.getHeader("traceparent");
        if (header == null) {
            return;
        }
        Matcher matcher = TRACEPARENT.matcher(header);
        if (!matcher.matches()) {
            return;
        }
        SpanContext incoming =
                SpanContext.createFromRemoteParent(
                        matcher.group(1),
                        matcher.group(2),
                        TraceFlags.fromHex(matcher.group(3), 0),
                        TraceState.getDefault());
        Span.current().addLink(incoming);
        request.setAttribute(LINKED_ATTRIBUTE, true);
    }
}
