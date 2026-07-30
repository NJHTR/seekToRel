package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository {
    Conversation save(Conversation conversation);

    Optional<Conversation> findById(TenantId tenantId, String conversationId);

    boolean update(Conversation conversation, long expectedVersion);

    ConversationMessage appendMessage(ConversationMessage message);

    /** Compatibility pagination contract using a strict timestamp cursor. */
    List<ConversationMessage> listMessages(TenantId tenantId, String conversationId,
                                           Instant before, int limit);

    /**
     * Returns newest-first history. The cursor is exclusive and compares both
     * timestamp and id, so messages sharing a timestamp are never skipped.
     */
    List<ConversationMessage> listMessagesAfter(TenantId tenantId, String conversationId,
                                                ConversationMessageCursor before, int limit);
}
