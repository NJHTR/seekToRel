package com.reportai.application.progress;

import com.reportai.application.config.properties.ReportAiProgressProperties;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventType;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressEventHubTest {
    @Test
    void subscribesAndCleansUpOnCompletion() {
        ReportAiProgressProperties properties = new ReportAiProgressProperties();
        ProgressEventHub hub = new ProgressEventHub(properties);
        SseEmitter emitter = hub.subscribe("task-1");
        assertEquals(1, hub.subscriberCount("task-1"));
        hub.publish(new ProgressEvent("event-1", "task-1", 0, ProgressEventType.PROGRESS,
                50, "halfway", Instant.now(), Map.of()));
        hub.close(emitter);
        assertEquals(0, hub.subscriberCount("task-1"));
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
}
