package com.reportai.application.config;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.scheduler.SchedulerRecoveryPoller;
import com.reportai.application.scheduler.SchedulerOutboxReconciler;
import com.reportai.scheduler.BoundedTaskScheduler;
import com.reportai.scheduler.ProgressEventSink;
import com.reportai.scheduler.SchedulerTaskStore;
import com.reportai.scheduler.TaskHandler;
import com.reportai.scheduler.TaskHandlerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
                                                     ObjectProvider<ProgressEventSink> events,
                                                     ReportAiSchedulerProperties properties) {
        ProgressEventSink eventSink = events.getIfAvailable(ProgressEventSink::noOp);
        return new BoundedTaskScheduler(store, handlers, eventSink, properties.toSchedulerConfig());
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
