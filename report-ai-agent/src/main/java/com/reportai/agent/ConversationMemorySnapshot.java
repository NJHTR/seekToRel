package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Durable summary of conversation messages up to an exclusive keyset boundary. */
public record ConversationMemorySnapshot(TenantId tenantId, String conversationId,
                                          ConversationMessageCursor through,
                                          String summary, long version,
                                          Instant createdAt, Instant updatedAt) {
    public ConversationMemorySnapshot {
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("conversationId is invalid");
        }
        through = Objects.requireNonNull(through, "through");
        if (through.timestampOnly()) {
            throw new IllegalArgumentException("snapshot cursor must include a message id");
        }
        if (summary == null || summary.isBlank() || summary.length() > 128 * 1024) {
            throw new IllegalArgumentException("summary is invalid");
        }
        if (version < 0) {
            throw new IllegalArgumentException("snapshot version must not be negative");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("snapshot updatedAt must not precede createdAt");
        }
    }
}
