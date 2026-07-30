package com.reportai.scheduler;

@FunctionalInterface
public interface TaskHandler {
    void handle(TaskExecutionContext context) throws Exception;
}
