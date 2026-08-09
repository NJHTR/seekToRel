package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.util.Objects;

/** A tenant-scoped conversation whose append-only history may need a new snapshot. */
public record ConversationCompactionCandidate(TenantId tenantId, String conversationId) {
    public ConversationCompactionCandidate {
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (conversationId == null || !conversationId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("conversationId is invalid");
        }
    }
}
