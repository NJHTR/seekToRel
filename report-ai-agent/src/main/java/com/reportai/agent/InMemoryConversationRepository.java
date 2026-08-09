package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Development/test adapter with the same tenant and CAS semantics as the PostgreSQL port. */
public final class InMemoryConversationRepository implements ConversationRepository {
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, ConversationMessage> messages = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        if (conversations.putIfAbsent(key(conversation.tenantId(), conversation.id()), conversation) != null) {
            throw new IllegalStateException("conversation already exists");
        }
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(TenantId tenantId, String conversationId) {
        return Optional.ofNullable(conversations.get(key(tenantId, conversationId)));
    }

    @Override
    public boolean update(Conversation conversation, long expectedVersion) {
        String key = key(conversation.tenantId(), conversation.id());
        return conversations.computeIfPresent(key, (ignored, current) ->
                current.version() == expectedVersion ? conversation : current) == conversation;
    }

    @Override
    public ConversationMessage appendMessage(ConversationMessage message) {
        if (findById(message.tenantId(), message.conversationId()).isEmpty()) {
            throw new IllegalArgumentException("conversation not found");
        }
        if (messages.putIfAbsent(message.id(), message) != null) {
            throw new IllegalStateException("conversation message already exists");
        }
        return message;
    }

    @Override
    public List<ConversationMessage> listMessages(TenantId tenantId, String conversationId,
                                                   java.time.Instant before, int limit) {
        return listMessagesAfter(tenantId, conversationId,
                before == null ? null : ConversationMessageCursor.atTimestamp(before), limit);
    }

    @Override
    public List<ConversationMessage> listMessagesAfter(TenantId tenantId, String conversationId,
                                                       ConversationMessageCursor before, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        if (findById(tenantId, conversationId).isEmpty()) {
            return List.of();
        }
        List<ConversationMessage> result = new ArrayList<>(messages.values().stream()
                .filter(message -> message.tenantId().equals(tenantId)
                        && message.conversationId().equals(conversationId))
                .filter(message -> isBefore(message, before))
                .sorted(Comparator.comparing(ConversationMessage::createdAt)
                        .thenComparing(ConversationMessage::id).reversed())
                .limit(limit)
                .toList());
        return List.copyOf(result);
    }

    private static boolean isBefore(ConversationMessage message, ConversationMessageCursor cursor) {
        if (cursor == null) {
            return true;
        }
        int time = message.createdAt().compareTo(cursor.createdAt());
        if (cursor.timestampOnly()) {
            return time < 0;
        }
        return time < 0 || (time == 0 && message.id().compareTo(cursor.messageId()) < 0);
    }

    private static String key(TenantId tenantId, String id) {
        return tenantId.value() + "\u0000" + id;
    }
}
