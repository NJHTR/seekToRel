package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

@FunctionalInterface
public interface ProgressEventSink {
    void publish(ProgressEvent event);

    /**
     * Publishes an event together with the trusted tenant that owns the task.
     *
     * <p>The original event-only method remains the functional method so existing
     * in-memory/test sinks continue to work. Tenant-aware sinks (for example a
     * durable history adapter) override this method and must never infer the
     * tenant from request data.</p>
     */
    default void publish(TenantId tenantId, ProgressEvent event) {
        publish(event);
    }

    /**
     * Publishes and returns the authoritative event representation. Durable adapters override
     * this hook to attach the database sequence before live observers receive the frame.
     */
    default ProgressEvent publishDurable(TenantId tenantId, ProgressEvent event) {
        publish(tenantId, event);
        return event;
    }

    static ProgressEventSink noOp() {
        return event -> { };
    }
}
