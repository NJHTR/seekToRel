package com.reportai.application.web.observability;

import com.reportai.application.config.properties.ReportAiObservabilityProperties;
import com.reportai.common.trace.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Records bounded-cardinality request metrics and emits slow request logs. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public final class RequestObservabilityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TIMER_NAME = "reportai.http.server.requests";
    private static final String SLOW_COUNTER_NAME = "reportai.http.server.slow_requests";

    private final ReportAiObservabilityProperties properties;
    private final MeterRegistry meterRegistry;

    public RequestObservabilityFilter(ReportAiObservabilityProperties properties,
                                      MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            record(request, response, System.nanoTime() - started);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, long elapsedNanos) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        int status = response.getStatus();
        String method = boundedToken(request.getMethod(), 16);
        String outcome = outcome(status);
        boolean slow = properties.getSlowRequestThreshold() != null
                && elapsedNanos >= properties.getSlowRequestThreshold().toNanos();

        if (properties.isMetricsEnabled()) {
            Timer.builder(TIMER_NAME)
                    .description("ReportAI HTTP server request latency")
                    .tags("method", method, "outcome", outcome)
                    .register(meterRegistry)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
            if (slow) {
                meterRegistry.counter(SLOW_COUNTER_NAME, "method", method, "outcome", outcome).increment();
            }
        }

        if (slow || status >= 500 || properties.isLogAllRequests()) {
            String traceId = response.getHeader(TRACE_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = TraceContext.getOrCreate();
            }
            String message = "http_request method=" + method
                    + " path=" + boundedPath(request.getRequestURI())
                    + " status=" + status
                    + " outcome=" + outcome
                    + " duration_ms=" + elapsedMillis
                    + " slow=" + slow
                    + " trace_id=" + boundedToken(traceId, 128);
            if (status >= 500) {
                log.error(message);
            } else if (slow) {
                log.warn(message);
            } else {
                log.info(message);
            }
        }
    }

    private String boundedPath(String path) {
        return sanitize(path, properties.getMaxPathLength());
    }

    private static String boundedToken(String value, int maxLength) {
        return sanitize(value, maxLength);
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", "?");
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private static String outcome(int status) {
        if (status >= 500) {
            return "SERVER_ERROR";
        }
        if (status >= 400) {
            return "CLIENT_ERROR";
        }
        if (status >= 300) {
            return "REDIRECTION";
        }
        return "SUCCESS";
    }
}
