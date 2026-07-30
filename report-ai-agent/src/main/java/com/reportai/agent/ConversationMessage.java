package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Append-only tenant-scoped conversation message with bounded content and metadata. */
public record ConversationMessage(String id, TenantId tenantId, String conversationId,
                                  ConversationRole role, String content,
                                  Map<String, String> metadata, Instant createdAt) {
    public ConversationMessage {
        id = identifier(id, "id");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        conversationId = identifier(conversationId, "conversationId");
        role = Objects.requireNonNull(role, "role");
        if (content == null || content.isBlank() || content.length() > 512 * 1024) {
            throw new IllegalArgumentException("conversation message content is invalid");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        if (metadata != null) {
            if (metadata.size() > 64) {
                throw new IllegalArgumentException("conversation message metadata is too large");
            }
            metadata.forEach((key, value) -> {
                if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                        || value == null || value.length() > 4096) {
                    throw new IllegalArgumentException("conversation message metadata is invalid");
                }
                copy.put(key, value);
            });
        }
        metadata = Map.copyOf(copy);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String identifier(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
