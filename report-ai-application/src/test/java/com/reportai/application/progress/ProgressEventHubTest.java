package com.reportai.application.progress;

import com.reportai.application.config.properties.ReportAiProgressProperties;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventCursor;
import com.reportai.scheduler.ProgressEventHistoryEntry;
import com.reportai.scheduler.ProgressEventHistoryRepository;
import com.reportai.scheduler.ProgressEventType;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressEventHubTest {
    @Test
    void springSelectsProductionConstructor() {
        ProgressEventHistoryRepository history = emptyHistory();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ReportAiProgressProperties.class);
            context.registerBean(ProgressEventHistoryRepository.class, () -> history);
            context.register(ProgressEventHub.class);
            context.refresh();

            assertNotNull(context.getBean(ProgressEventHub.class));
        }
    }

    @Test
    void subscribesAndCleansUpOnCompletion() {
        ReportAiProgressProperties properties = new ReportAiProgressProperties();
        ProgressEventHub hub = new ProgressEventHub(properties);
        SseEmitter emitter = hub.subscribe("task-1");
        assertEquals(1, hub.subscriberCount("task-1"));
        hub.publish(new ProgressEvent("event-1", "task-1", 0, ProgressEventType.PROGRESS,
                50, "halfway", Instant.now(), Map.of()));
        assertEquals(1, hub.replayEventCount("task-1"));
        hub.close(emitter);
        assertEquals(0, hub.subscriberCount("task-1"));
    }

    @Test
    void keepsOnlyTheConfiguredReplayTail() {
        ReportAiProgressProperties properties = new ReportAiProgressProperties();
        properties.setMaxReplayEventsPerTask(2);
        ProgressEventHub hub = new ProgressEventHub(properties);
        for (int i = 0; i < 3; i++) {
            hub.publish(new ProgressEvent("event-" + i, "task-1", 0, ProgressEventType.PROGRESS,
                    i, "step-" + i, Instant.now(), Map.of()));
        }
        assertEquals(2, hub.replayEventCount("task-1"));
    }

    @Test
    void enforcesSubscriberLimitAndTaskIdPolicy() {
        ReportAiProgressProperties properties = new ReportAiProgressProperties();
        properties.setMaxSubscribersPerTask(1);
        ProgressEventHub hub = new ProgressEventHub(properties);
        hub.subscribe("task-1");
        assertThrows(ProgressEventHub.ProgressSubscriptionLimitException.class,
                () -> hub.subscribe("task-1"));
        assertThrows(IllegalArgumentException.class, () -> hub.subscribe("../secret"));
    }

    @Test
    void enforcesGlobalSubscriberLimitAndReleasesSlots() {
        ReportAiProgressProperties properties = new ReportAiProgressProperties();
        properties.setMaxSubscribersPerTask(1);
        properties.setMaxTotalSubscribers(1);
        ProgressEventHub hub = new ProgressEventHub(properties);
        SseEmitter first = hub.subscribe("task-1");
        assertThrows(ProgressEventHub.ProgressSubscriptionLimitException.class,
                () -> hub.subscribe("task-2"));
        hub.close(first);
        SseEmitter second = hub.subscribe("task-2");
        hub.close(second);
    }

    @Test
    void reconnectPassesLastEventSequenceToTenantScopedDurableHistory() {
        AtomicReference<ProgressEventCursor> cursor = new AtomicReference<>();
        ProgressEventHistoryRepository history = new ProgressEventHistoryRepository() {
            @Override
            public ProgressEventHistoryEntry append(com.reportai.common.tenant.TenantId tenantId,
                                                    ProgressEvent event) {
                return new ProgressEventHistoryEntry(1, event);
            }

            @Override
            public List<ProgressEventHistoryEntry> listAfter(com.reportai.common.tenant.TenantId tenantId,
                                                              String taskId,
                                                              ProgressEventCursor after,
                                                              int limit) {
                cursor.set(after);
                return new ArrayList<>();
            }
        };
        ProgressEventHub hub = new ProgressEventHub(new ReportAiProgressProperties(), history);

        SseEmitter emitter = hub.subscribe(com.reportai.common.tenant.TenantId.of("tenant-a"),
                "task-1", 17L);

        assertEquals(17L, cursor.get().eventSequence());
        hub.close(emitter);
    }

    @Test
    void clientDisconnectDuringReplayCompletesEmitterWithoutRaisingJsonError() {
        ProgressEventHistoryRepository history = new ProgressEventHistoryRepository() {
            @Override
            public ProgressEventHistoryEntry append(com.reportai.common.tenant.TenantId tenantId,
                                                    ProgressEvent event) {
                return new ProgressEventHistoryEntry(1, event);
            }

            @Override
            public List<ProgressEventHistoryEntry> listAfter(com.reportai.common.tenant.TenantId tenantId,
                                                              String taskId,
                                                              ProgressEventCursor after,
                                                              int limit) {
                return List.of(new ProgressEventHistoryEntry(1,
                        new ProgressEvent("event-1", taskId, 1, ProgressEventType.PROGRESS,
                                10, "started", Instant.now(), Map.of())));
            }
        };
        ProgressEventHub hub = new ProgressEventHub(new ReportAiProgressProperties(), history);
        SseEmitter emitter = hub.subscribe(com.reportai.common.tenant.TenantId.of("tenant-a"), "task-1", null);

        assertEquals(1, hub.subscriberCount(com.reportai.common.tenant.TenantId.of("tenant-a"), "task-1"));
        hub.close(emitter);
    }

    private static ProgressEventHistoryRepository emptyHistory() {
        return new ProgressEventHistoryRepository() {
            @Override
            public ProgressEventHistoryEntry append(com.reportai.common.tenant.TenantId tenantId,
                                                    ProgressEvent event) {
                return new ProgressEventHistoryEntry(1, event);
            }

            @Override
            public List<ProgressEventHistoryEntry> listAfter(com.reportai.common.tenant.TenantId tenantId,
                                                              String taskId,
                                                              ProgressEventCursor after,
                                                              int limit) {
                return List.of();
            }
        };
    }
}
