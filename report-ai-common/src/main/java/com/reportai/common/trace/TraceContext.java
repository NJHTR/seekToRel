package com.reportai.common.trace;

public final class TraceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        CURRENT.set(traceId);
    }

    public static String getOrCreate() {
        String traceId = CURRENT.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
            CURRENT.set(traceId);
        }
        return traceId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
