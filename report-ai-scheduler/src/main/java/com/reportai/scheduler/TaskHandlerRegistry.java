package com.reportai.scheduler;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskHandlerRegistry {
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public void register(String type, TaskHandler handler) {
        if (type == null || type.isBlank() || handler == null) {
            throw new IllegalArgumentException("type and handler are required");
        }
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("task handler already registered: " + type);
        }
    }

    public TaskHandler require(String type) {
        return Objects.requireNonNull(handlers.get(type), "no task handler registered for " + type);
    }
}
