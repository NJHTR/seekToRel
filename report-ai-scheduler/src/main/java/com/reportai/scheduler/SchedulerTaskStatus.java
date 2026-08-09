package com.reportai.scheduler;

/** Durable lifecycle states. A store must persist these states atomically. */
public enum SchedulerTaskStatus {
    QUEUED, RUNNING, SUCCEEDED, RETRY_WAITING, FAILED, CANCELLED
}
