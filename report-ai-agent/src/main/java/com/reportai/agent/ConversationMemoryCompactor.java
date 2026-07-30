package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.util.List;

/** Creates a bounded, non-authoritative summary for messages omitted from a prompt. */
@FunctionalInterface
public interface ConversationMemoryCompactor {
    String compact(TenantId tenantId, String conversationId,
                   List<ConversationMessage> omittedMessages, int maxChars);
}
