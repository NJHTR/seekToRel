package com.reportai.application.config;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.scheduler.SchedulerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportAiSchedulerPropertiesTest {
    @Test
    void mapsExternalSettingsToBoundedSchedulerConfig() {
        ReportAiSchedulerProperties properties = new ReportAiSchedulerProperties();
        properties.setWorkerCount(2);
        properties.setQueueCapacity(8);
        properties.setLeaseDuration(Duration.ofSeconds(30));
        properties.setExecutionTimeout(Duration.ofMinutes(1));
        properties.setMaxProgressMessageLength(400);

        SchedulerConfig config = properties.toSchedulerConfig();

        assertEquals(2, config.workerCount());
        assertEquals(8, config.queueCapacity());
        assertEquals(Duration.ofSeconds(30), config.leaseDuration());
        assertEquals(Duration.ofMinutes(1), config.executionTimeout());
        assertEquals(400, config.maxProgressMessageLength());
    }

    @Test
    void rejectsInvalidWorkerSettings() {
        ReportAiSchedulerProperties properties = new ReportAiSchedulerProperties();
        properties.setWorkerCount(0);

        assertThrows(IllegalArgumentException.class, properties::toSchedulerConfig);
    }
}
