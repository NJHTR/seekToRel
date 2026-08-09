package com.reportai.application.progress;

import com.reportai.application.config.properties.ReportAiProgressProperties;
import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventHistoryEntry;
import com.reportai.scheduler.ProgressEventHistoryRepository;
import com.reportai.scheduler.ProgressEventCursor;
import com.reportai.scheduler.ProgressEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded live fan-out for progress events. PostgreSQL history is replayed on reconnect and the
 * local tail is only a low-latency fallback; it is never presented as the authoritative audit log.
 */
@Component
public final class ProgressEventHub implements ProgressEventSink {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressEventHub.class);
    private static final String LEGACY_TENANT = "_legacy";
    private final ConcurrentHashMap<String, TaskSubscriptions> subscriptions = new ConcurrentHashMap<>();
    private final Object historyLock = new Object();
    private final LinkedHashMap<String, Deque<ProgressEvent>> localHistory = new LinkedHashMap<>(16, 0.75f, true);
    private final ReportAiProgressProperties properties;
    private final ProgressEventHistoryRepository durableHistory;
    private final AtomicInteger totalSubscribers = new AtomicInteger();

    /** Constructor retained for focused unit tests that do not provide PostgreSQL. */
    public ProgressEventHub(ReportAiProgressProperties properties) {
        this(properties, null);
    }

    @Autowired
    public ProgressEventHub(ReportAiProgressProperties properties,
                            ProgressEventHistoryRepository durableHistory) {
        this.properties = properties;
        this.durableHistory = durableHistory;
        validateProperties(properties);
    }

    public SseEmitter subscribe(String taskId) {
        return subscribe(null, taskId, null);
    }

    /** Opens a tenant-bound stream and replays events strictly after the durable sequence cursor. */
    public SseEmitter subscribe(TenantId tenantId, String taskId, Long afterSequence) {
        validateTaskId(taskId);
        if (afterSequence != null && afterSequence < 0) {
            throw new IllegalArgumentException("last event id is invalid");
        }
        String key = key(tenantId, taskId);
        TaskSubscriptions taskSubscriptions = subscriptions.computeIfAbsent(key,
                ignored -> new TaskSubscriptions());
        if (totalSubscribers.incrementAndGet() > properties.getMaxTotalSubscribers()) {
            totalSubscribers.decrementAndGet();
            throw new ProgressSubscriptionLimitException("too many progress subscribers");
        }
        SseEmitter emitter = new SseEmitter(properties.getEmitterTimeout().toMillis());
        Subscription subscription = new Subscription(key, emitter);
        synchronized (taskSubscriptions) {
            if (taskSubscriptions.items.size() >= properties.getMaxSubscribersPerTask()) {
                totalSubscribers.decrementAndGet();
                throw new ProgressSubscriptionLimitException("too many progress subscribers");
            }
            taskSubscriptions.items.add(subscription);
        }
        Runnable cleanup = () -> remove(subscription);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
            for (ProgressEvent event : replay(tenantId, taskId, afterSequence)) {
                if (!subscription.send(event)) {
                    cleanup.run();
                    emitter.complete();
                    break;
                }
            }
        } catch (IOException | RuntimeException failure) {
            cleanup.run();
            LOGGER.debug("progress stream initial replay failed taskId={}", taskId, failure);
            // A browser refresh, proxy timeout, or client navigation can close
            // an SSE socket while replay is flushing. Do not route that normal
            // disconnect through the JSON exception handler with a
            // text/event-stream content type; simply finish this subscription.
            emitter.complete();
        }
        return emitter;
    }

    @Override
    public void publish(ProgressEvent event) {
        publish(null, event);
    }

    @Override
    public void publish(TenantId tenantId, ProgressEvent event) {
        if (event == null) {
            return;
        }
        String key = key(tenantId, event.taskId());
        remember(key, event);
        TaskSubscriptions taskSubscriptions = subscriptions.get(key);
        if (taskSubscriptions == null) {
            return;
        }
        for (Subscription subscription : taskSubscriptions.items) {
            if (!subscription.send(event)) {
                remove(subscription);
            }
        }
    }

    int subscriberCount(String taskId) {
        return subscriberCount(null, taskId);
    }

    int subscriberCount(TenantId tenantId, String taskId) {
        TaskSubscriptions current = subscriptions.get(key(tenantId, taskId));
        return current == null ? 0 : current.items.size();
    }

    int replayEventCount(String taskId) {
        synchronized (historyLock) {
            Deque<ProgressEvent> events = localHistory.get(key(null, taskId));
            return events == null ? 0 : events.size();
        }
    }

    void close(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        subscriptions.values().forEach(current -> current.items.stream()
                .filter(subscription -> subscription.emitter() == emitter)
                .findFirst()
                .ifPresent(subscription -> {
                    remove(subscription);
                    emitter.complete();
                }));
    }

    private void remove(Subscription subscription) {
        TaskSubscriptions current = subscriptions.get(subscription.key());
        if (current == null) {
            return;
        }
        synchronized (current) {
            if (!current.items.remove(subscription)) {
                return;
            }
            totalSubscribers.decrementAndGet();
            if (current.items.isEmpty()) {
                subscriptions.remove(subscription.key(), current);
            }
        }
    }

    private List<ProgressEvent> replay(TenantId tenantId, String taskId, Long afterSequence) {
        Map<String, ProgressEvent> events = new LinkedHashMap<>();
        if (tenantId != null && durableHistory != null) {
            try {
                int limit = Math.min(properties.getMaxReplayEventsPerTask(),
                        ProgressEventHistoryRepository.MAX_PAGE_SIZE);
                List<ProgressEventHistoryEntry> durable = durableHistory.listAfter(tenantId, taskId,
                        afterSequence == null || afterSequence == 0 ? null : new ProgressEventCursor(afterSequence), limit);
                durable.stream().map(ProgressEventHistoryEntry::event)
                        .forEach(event -> events.put(event.eventId(), event));
            } catch (RuntimeException failure) {
                // A transient history outage must not prevent a live stream. The client will retry
                // with Last-Event-ID and the failure remains visible in the server logs/metrics.
                LOGGER.warn("durable progress history replay unavailable taskId={}", taskId, failure);
            }
        }
        synchronized (historyLock) {
            Deque<ProgressEvent> local = localHistory.get(key(tenantId, taskId));
            if (local != null) {
                local.stream()
                        .filter(event -> afterSequence == null || afterSequence == 0
                                || event.eventSequence() == 0 || event.eventSequence() > afterSequence)
                        .forEach(event -> events.putIfAbsent(event.eventId(), event));
            }
        }
        return events.values().stream()
                .sorted(Comparator.comparingLong(ProgressEvent::eventSequence)
                        .thenComparing(ProgressEvent::occurredAt)
                        .thenComparing(ProgressEvent::eventId))
                .toList();
    }

    private void remember(String key, ProgressEvent event) {
        synchronized (historyLock) {
            Deque<ProgressEvent> events = localHistory.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            events.addLast(event);
            while (events.size() > properties.getMaxReplayEventsPerTask()) {
                events.removeFirst();
            }
            while (localHistory.size() > properties.getMaxReplayTasks()) {
                localHistory.remove(localHistory.keySet().iterator().next());
            }
        }
    }

    private static String key(TenantId tenantId, String taskId) {
        return (tenantId == null ? LEGACY_TENANT : tenantId.value()) + ":" + taskId;
    }

    private static void validateTaskId(String taskId) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("taskId is invalid");
        }
    }

    private static void validateProperties(ReportAiProgressProperties properties) {
        if (properties == null || properties.getEmitterTimeout() == null
                || properties.getEmitterTimeout().isNegative()
                || properties.getEmitterTimeout().isZero()
                || properties.getMaxSubscribersPerTask() < 1
                || properties.getMaxTotalSubscribers() < properties.getMaxSubscribersPerTask()
                || properties.getMaxReplayEventsPerTask() < 1
                || properties.getMaxReplayTasks() < 1) {
            throw new IllegalArgumentException("invalid progress stream configuration");
        }
    }

    private static final class TaskSubscriptions {
        private final CopyOnWriteArrayList<Subscription> items = new CopyOnWriteArrayList<>();
    }

    private record Subscription(String key, SseEmitter emitter) {
        private boolean send(ProgressEvent event) {
            synchronized (emitter) {
                try {
                    String id = event.eventSequence() > 0 ? String.valueOf(event.eventSequence()) : event.eventId();
                    emitter.send(SseEmitter.event().id(id).name(event.type().name()).data(event));
                    return true;
                } catch (IOException | RuntimeException failure) {
                    return false;
                }
            }
        }
    }

    public static final class ProgressSubscriptionLimitException extends RuntimeException {
        public ProgressSubscriptionLimitException(String message) {
            super(message);
        }
    }
}
