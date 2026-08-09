package com.reportai.common.contract;

import java.util.Map;
import java.util.Objects;

/** Immutable trace/correlation metadata propagated with commands and events. */
public record TraceMetadata(TraceId traceId, SpanId spanId, SpanId parentSpanId,
                            CorrelationId correlationId, Map<String, String> baggage) {
    public TraceMetadata {
        traceId = Objects.requireNonNull(traceId, "traceId");
        spanId = Objects.requireNonNull(spanId, "spanId");
        correlationId = Objects.requireNonNull(correlationId, "correlationId");
        baggage = baggage == null ? Map.of() : boundedBaggage(baggage);
    }

    public static TraceMetadata root(TraceId traceId, CorrelationId correlationId) {
        return new TraceMetadata(traceId, new SpanId(traceId.value()), null, correlationId, Map.of());
    }

    private static Map<String, String> boundedBaggage(Map<String, String> source) {
        if (source.size() > 16) {
            throw new IllegalArgumentException("trace baggage is too large");
        }
        java.util.Map<String, String> copy = new java.util.TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || !key.matches("[a-z][a-z0-9_.-]{0,31}")
                    || value == null || value.length() > 256
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("trace baggage is invalid");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
