package com.reportai.application.progress;

import com.reportai.application.config.properties.ReportAiProgressProperties;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventSink;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-local live progress fan-out. Durable task state remains the source of truth; this hub
 * only transports events emitted while a client is connected.
 */
@Component
public final class ProgressEventHub implements ProgressEventSink {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription>> subscriptions =
            new ConcurrentHashMap<>();
    private final ReportAiProgressProperties properties;

    public ProgressEventHub(ReportAiProgressProperties properties) {
        this.properties = properties;
        validateProperties(properties);
    }

    public SseEmitter subscribe(String taskId) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("taskId is invalid");
        }
        CopyOnWriteArrayList<Subscription> taskSubscriptions = subscriptions.computeIfAbsent(taskId,
                ignored -> new CopyOnWriteArrayList<>());
        if (taskSubscriptions.size() >= properties.getMaxSubscribersPerTask()) {
            throw new ProgressSubscriptionLimitException("too many progress subscribers");
        }
        SseEmitter emitter = new SseEmitter(properties.getEmitterTimeout().toMillis());
        Subscription subscription = new Subscription(taskId, emitter);
        taskSubscriptions.add(subscription);
        Runnable cleanup = () -> remove(subscription);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException failure) {
            cleanup.run();
            emitter.completeWithError(failure);
        }
        return emitter;
    }

    @Override
    public void publish(ProgressEvent event) {
        if (event == null) {
            return;
        }
        CopyOnWriteArrayList<Subscription> taskSubscriptions = subscriptions.get(event.taskId());
        if (taskSubscriptions == null) {
            return;
        }
        for (Subscription subscription : taskSubscriptions) {
            if (!subscription.send(event)) {
                remove(subscription);
            }
        }
    }

    int subscriberCount(String taskId) {
        CopyOnWriteArrayList<Subscription> current = subscriptions.get(taskId);
        return current == null ? 0 : current.size();
    }

    /** Explicitly closes a stream when an owning component needs deterministic cleanup. */
    void close(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        subscriptions.values().forEach(current -> current.stream()
                .filter(subscription -> subscription.emitter() == emitter)
                .findFirst()
                .ifPresent(subscription -> {
                    remove(subscription);
                    emitter.complete();
                }));
    }

    private void remove(Subscription subscription) {
        CopyOnWriteArrayList<Subscription> current = subscriptions.get(subscription.taskId());
        if (current != null && current.remove(subscription) && current.isEmpty()) {
            subscriptions.remove(subscription.taskId(), current);
        }
    }

    private static void validateProperties(ReportAiProgressProperties properties) {
        if (properties == null || properties.getEmitterTimeout() == null
                || properties.getEmitterTimeout().isNegative()
                || properties.getEmitterTimeout().isZero()
                || properties.getMaxSubscribersPerTask() < 1) {
            throw new IllegalArgumentException("invalid progress stream configuration");
        }
    }

    private record Subscription(String taskId, SseEmitter emitter) {
        private boolean send(ProgressEvent event) {
            synchronized (emitter) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(event.eventId())
                            .name(event.type().name())
                            .data(event));
                    return true;
                } catch (IOException | IllegalStateException failure) {
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
