package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Delivers each event to independent sinks. A failing observer must not prevent the live SSE
 * observer or the scheduler worker from completing; the failure is logged with the event id.
 */
public final class FanOutProgressEventSink implements ProgressEventSink {
    private static final int MAX_DURABLE_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 25L;
    private static final Logger LOGGER = LoggerFactory.getLogger(FanOutProgressEventSink.class);
    private final ProgressEventSink durable;
    private final List<ProgressEventSink> observers;
    private final AtomicLong durableFailureCount = new AtomicLong();

    public FanOutProgressEventSink(Iterable<? extends ProgressEventSink> delegates) {
        Objects.requireNonNull(delegates, "delegates");
        List<ProgressEventSink> copy = new ArrayList<>();
        for (ProgressEventSink delegate : delegates) {
            copy.add(Objects.requireNonNull(delegate, "delegate"));
        }
        if (copy.isEmpty()) {
            this.durable = ProgressEventSink.noOp();
            this.observers = List.of();
        } else {
            this.durable = copy.getFirst();
            this.observers = List.copyOf(copy.subList(1, copy.size()));
        }
    }

    public FanOutProgressEventSink(ProgressEventSink durable,
                                   Iterable<? extends ProgressEventSink> observers) {
        this.durable = Objects.requireNonNull(durable, "durable");
        Objects.requireNonNull(observers, "observers");
        List<ProgressEventSink> copy = new ArrayList<>();
        for (ProgressEventSink observer : observers) {
            copy.add(Objects.requireNonNull(observer, "observer"));
        }
        this.observers = List.copyOf(copy);
    }

    @Override
    public void publish(ProgressEvent event) {
        ProgressEvent published = invokeDurable(null, event);
        if (published != null) observers.forEach(observer -> invoke(observer, null, published));
    }

    @Override
    public void publish(TenantId tenantId, ProgressEvent event) {
        ProgressEvent published = invokeDurable(tenantId, event);
        if (published != null) observers.forEach(observer -> invoke(observer, tenantId, published));
    }

    /** Number of durable writes that failed since this sink was created; suitable for health probes. */
    public long durableFailureCount() {
        return durableFailureCount.get();
    }

    private ProgressEvent invokeDurable(TenantId tenantId, ProgressEvent event) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_DURABLE_ATTEMPTS; attempt++) {
            try {
                return durable.publishDurable(tenantId, event);
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (attempt < MAX_DURABLE_ATTEMPTS) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(RETRY_BACKOFF_MILLIS * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastFailure != null) {
            // Durable failures are intentionally logged at error level and are not swallowed as
            // ordinary observer noise. The event is not broadcast as if it were durable; the
            // scheduler continues its business transition and operators can alert on this metric.
            LOGGER.error("durable progress event history write failed eventId={} taskId={} sink={}",
                    event == null ? "unknown" : event.eventId(),
                    event == null ? "unknown" : event.taskId(),
                    durable.getClass().getName(), lastFailure);
            durableFailureCount.incrementAndGet();
        }
        return null;
    }

    private static void invoke(ProgressEventSink delegate, TenantId tenantId, ProgressEvent event) {
        try {
            if (tenantId == null) {
                delegate.publish(event);
            } else {
                delegate.publish(tenantId, event);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("progress event sink failed eventId={} taskId={} sink={}",
                    event == null ? "unknown" : event.eventId(),
                    event == null ? "unknown" : event.taskId(),
                    delegate.getClass().getName(), failure);
        }
    }
}
