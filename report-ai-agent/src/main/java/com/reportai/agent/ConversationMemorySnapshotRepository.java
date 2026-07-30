package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.util.Optional;

/** Tenant-scoped persistence port for resumable conversation memory summaries. */
public interface ConversationMemorySnapshotRepository {
    Optional<ConversationMemorySnapshot> find(TenantId tenantId, String conversationId);

    ConversationMemorySnapshot save(ConversationMemorySnapshot snapshot);

    boolean update(ConversationMemorySnapshot snapshot, long expectedVersion);
}
