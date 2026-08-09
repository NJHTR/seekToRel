package com.reportai.application.config;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.scheduler.SchedulerRecoveryPoller;
import com.reportai.application.scheduler.SchedulerOutboxReconciler;
import com.reportai.scheduler.BoundedTaskScheduler;
import com.reportai.scheduler.FanOutProgressEventSink;
import com.reportai.scheduler.PersistedProgressEventSink;
import com.reportai.scheduler.ProgressEventHistoryRepository;
import com.reportai.scheduler.ProgressEventSink;
import com.reportai.scheduler.SchedulerTaskStore;
import com.reportai.scheduler.TaskHandler;
import com.reportai.scheduler.TaskHandlerRegistry;
import com.reportai.application.progress.ProgressEventHub;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring wiring for the durable, bounded task scheduler. */
@Configuration(proxyBeanMethods = false)
public class SchedulerConfiguration {
    private static final String REPORT_TASK_HANDLER_BEAN = "reportTaskHandler";
    private static final String REPORT_TASK_TYPE = "report-task";

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public TaskHandlerRegistry taskHandlerRegistry(
            @Qualifier(REPORT_TASK_HANDLER_BEAN) TaskHandler reportTaskHandler) {
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        registry.register(REPORT_TASK_TYPE, reportTaskHandler);
        return registry;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public BoundedTaskScheduler boundedTaskScheduler(SchedulerTaskStore store,
                                                     TaskHandlerRegistry handlers,
                                                     @Qualifier("progressEventSink") ProgressEventSink events,
                                                     ReportAiSchedulerProperties properties) {
        return new BoundedTaskScheduler(store, handlers, events, properties.toSchedulerConfig());
    }

    @Bean(name = "progressEventSink")
    @Primary
    @ConditionalOnProperty(prefix = "report-ai.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ProgressEventSink progressEventSink(ProgressEventHub liveHub,
                                               ProgressEventHistoryRepository history) {
        return new FanOutProgressEventSink(new PersistedProgressEventSink(history),
                java.util.List.of(liveHub));
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SchedulerRecoveryPoller schedulerRecoveryPoller(BoundedTaskScheduler scheduler,
                                                            ReportAiSchedulerProperties properties) {
        return new SchedulerRecoveryPoller(scheduler, properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.scheduler", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SchedulerOutboxReconciler schedulerOutboxReconciler(
            com.reportai.scheduler.SchedulerOutboxRepository outbox,
            BoundedTaskScheduler scheduler,
            ReportAiSchedulerProperties properties) {
        return new SchedulerOutboxReconciler(outbox, scheduler, properties);
    }
}
