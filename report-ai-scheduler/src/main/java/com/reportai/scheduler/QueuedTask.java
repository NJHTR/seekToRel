package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Immutable command persisted before it is submitted to a worker. */
public record QueuedTask(String id, TenantId tenantId, String type, String payload,
                         String idempotencyKey, RetryPolicy retryPolicy, Instant createdAt) {
    public QueuedTask {
        if (id == null || id.isBlank() || tenantId == null || type == null || type.isBlank()
                || payload == null || idempotencyKey == null || idempotencyKey.isBlank()
                || retryPolicy == null || createdAt == null) {
            throw new IllegalArgumentException("invalid queued task");
        }
        if (type.length() > 128 || idempotencyKey.length() > 256 || payload.length() > 2_000_000) {
            throw new IllegalArgumentException("task field exceeds limits");
        }
    }

    public String requestHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((type + '\u0000' + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
