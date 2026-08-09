package com.reportai.scheduler;

import java.time.Instant;
import java.util.Map;

public interface TaskExecutionContext {
    String taskId();
    String type();
    String payload();
    int attempt();
    Instant deadline();
    boolean timedOut();
    /** Extends the durable lease using an optimistic-lock transition. */
    boolean renewLease();
    void progress(int percent, String message, Map<String, String> attributes);
}
