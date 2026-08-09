package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Tenant-owned conversation aggregate metadata. Messages are stored separately and append-only. */
public record Conversation(String id, TenantId tenantId, String taskId, String title,
                           long version, Instant createdAt, Instant updatedAt) {
    public Conversation {
        id = identifier(id, "id");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (taskId != null) {
            taskId = identifier(taskId, "taskId");
        }
        title = title == null ? "" : title.trim();
        if (title.length() > 256) {
            throw new IllegalArgumentException("conversation title is too long");
        }
        if (version < 0) {
            throw new IllegalArgumentException("conversation version must not be negative");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("conversation updatedAt must not precede createdAt");
        }
    }

    public Conversation touch(long nextVersion, Instant now) {
        if (nextVersion != version + 1) {
            throw new IllegalArgumentException("conversation version must advance by one");
        }
        return new Conversation(id, tenantId, taskId, title, nextVersion, createdAt, now);
    }

    private static String identifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
