package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Development/test adapter with tenant isolation and optimistic locking. */
public final class InMemoryConversationMemorySnapshotRepository
        implements ConversationMemorySnapshotRepository {
    private final Map<String, ConversationMemorySnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<ConversationMemorySnapshot> find(TenantId tenantId, String conversationId) {
        return Optional.ofNullable(snapshots.get(key(tenantId, conversationId)));
    }

    @Override
    public ConversationMemorySnapshot save(ConversationMemorySnapshot snapshot) {
        if (snapshots.putIfAbsent(key(snapshot.tenantId(), snapshot.conversationId()), snapshot) != null) {
            throw new IllegalStateException("conversation memory snapshot already exists");
        }
        return snapshot;
    }

    @Override
    public boolean update(ConversationMemorySnapshot snapshot, long expectedVersion) {
        String key = key(snapshot.tenantId(), snapshot.conversationId());
        return snapshots.computeIfPresent(key, (ignored, current) ->
                current.version() == expectedVersion ? snapshot : current) == snapshot;
    }

    private static String key(TenantId tenantId, String conversationId) {
        return tenantId.value() + "\u0000" + conversationId;
    }
}
