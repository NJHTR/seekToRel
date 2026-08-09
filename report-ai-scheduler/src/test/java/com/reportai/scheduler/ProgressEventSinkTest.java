package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressEventSinkTest {
    private static final TenantId TENANT = TenantId.of("tenant-a");

    @Test
    void fanOutPreservesTenantAwareDeliveryAndDoesNotExposeUnsafeAttributes() {
        AtomicInteger durableCalls = new AtomicInteger();
        AtomicInteger liveCalls = new AtomicInteger();
        FanOutProgressEventSink sink = new FanOutProgressEventSink(List.of(
                new ProgressEventSink() {
                    @Override
                    public void publish(ProgressEvent event) {
                        durableCalls.incrementAndGet();
                    }

                    @Override
                    public void publish(TenantId tenantId, ProgressEvent event) {
                        assertEquals(TENANT, tenantId);
                        durableCalls.incrementAndGet();
                    }
                },
                (ProgressEventSink) event -> liveCalls.incrementAndGet()));

        ProgressEvent event = new ProgressEvent("event-1", "task-1", 0,
                ProgressEventType.PROGRESS, 20, "step", Instant.now(),
                Map.of("phase", "parse", "error", "secret details must be dropped"));
        sink.publish(TENANT, event);

        assertEquals(1, durableCalls.get());
        assertEquals(1, liveCalls.get());
        assertEquals(Map.of("phase", "parse"), event.attributes());
    }

    @Test
    void oneFailingObserverDoesNotBlockTheOtherObserver() {
        AtomicInteger delivered = new AtomicInteger();
        FanOutProgressEventSink sink = new FanOutProgressEventSink(ProgressEventSink.noOp(), List.of(
                new ProgressEventSink() {
                    @Override
                    public void publish(ProgressEvent event) {
                        throw new IllegalStateException("observer unavailable");
                    }
                },
                (ProgressEventSink) event -> delivered.incrementAndGet()));

        sink.publish(TENANT, new ProgressEvent("event-2", "task-2", 0,
                ProgressEventType.STARTED, 0, "started", Instant.now(), Map.of()));

        assertTrue(delivered.get() == 1);
    }

    @Test
    void durableFailureIsCountedAndEventIsNotBroadcastAsDurable() {
        AtomicInteger delivered = new AtomicInteger();
        FanOutProgressEventSink sink = new FanOutProgressEventSink(
                event -> { throw new IllegalStateException("database unavailable"); },
                List.of((ProgressEventSink) event -> delivered.incrementAndGet()));

        sink.publish(TENANT, new ProgressEvent("event-3", "task-3", 0,
                ProgressEventType.PROGRESS, 1, "step", Instant.now(), Map.of()));

        assertEquals(1, sink.durableFailureCount());
        assertEquals(0, delivered.get());
    }

    @Test
    void fanOutForwardsDurableCanonicalSequenceToLiveObservers() {
        AtomicInteger observedSequence = new AtomicInteger();
        ProgressEventSink durable = new ProgressEventSink() {
            @Override
            public void publish(ProgressEvent event) {
                throw new AssertionError("publishDurable must be used for the durable sink");
            }

            @Override
            public ProgressEvent publishDurable(TenantId tenantId, ProgressEvent event) {
                return new ProgressEvent(event.eventId(), event.taskId(), event.attempt(), event.type(),
                        event.percent(), event.message(), event.occurredAt(), event.attributes(), 42L);
            }
        };
        FanOutProgressEventSink sink = new FanOutProgressEventSink(durable,
                List.of((ProgressEventSink) event -> observedSequence.set((int) event.eventSequence())));

        sink.publish(TENANT, new ProgressEvent("event-sequence", "task-sequence", 0,
                ProgressEventType.PROGRESS, 50, "halfway", Instant.now(), Map.of()));

        assertEquals(42, observedSequence.get());
    }
}
