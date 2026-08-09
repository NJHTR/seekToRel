package com.reportai.common.contract;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned immutable description of the inputs supplied to an Agent or command. */
public record InputSnapshot(int schemaVersion, SnapshotId snapshotId, TenantId tenantId,
                            List<SnapshotRef> references, Sha256Digest contentDigest,
                            List<SnapshotId> parentSnapshotIds, Instant createdAt,
                            Map<String, String> metadata) {
    public InputSnapshot {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("snapshot schemaVersion must be positive");
        }
        snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        references = references == null ? List.of() : List.copyOf(references);
        if (references.size() > 256) {
            throw new IllegalArgumentException("snapshot contains too many references");
        }
        if (references.stream().map(SnapshotRef::name).distinct().count() != references.size()) {
            throw new IllegalArgumentException("snapshot references must have unique names");
        }
        contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        parentSnapshotIds = parentSnapshotIds == null ? List.of() : List.copyOf(parentSnapshotIds);
        if (parentSnapshotIds.size() > 32) {
            throw new IllegalArgumentException("snapshot has too many parents");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        metadata = boundedMetadata(metadata);
    }

    private static Map<String, String> boundedMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > 32) {
            throw new IllegalArgumentException("snapshot metadata is too large");
        }
        java.util.Map<String, String> copy = new java.util.TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                    || value == null || value.length() > 512
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("snapshot metadata is invalid");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
