package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationRepositoryTest {
    @Test
    void enforcesTenantIsolationAndCasUpdates() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant now = Instant.now();
        Conversation conversation = new Conversation("conversation-1", tenant, null, "Experiment", 0, now, now);
        repository.save(conversation);

        Conversation next = conversation.touch(1, now.plusSeconds(1));
        assertTrue(repository.update(next, 0));
        assertFalse(repository.update(conversation, 0));
        assertTrue(repository.findById(TenantId.of("tenant-b"), conversation.id()).isEmpty());
    }

    @Test
    void appendsAndReadsNewestMessagesWithinBoundedPage() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant now = Instant.now();
        Conversation conversation = new Conversation("conversation-1", tenant, null, "", 0, now, now);
        repository.save(conversation);
        repository.appendMessage(new ConversationMessage("message-1", tenant, conversation.id(),
                ConversationRole.USER, "hello", Map.of("source", "ui"), now.plusSeconds(1)));
        repository.appendMessage(new ConversationMessage("message-2", tenant, conversation.id(),
                ConversationRole.ASSISTANT, "world", Map.of(), now.plusSeconds(2)));

        List<ConversationMessage> messages = repository.listMessages(tenant, conversation.id(), null, 10);
        assertEquals(List.of("message-2", "message-1"), messages.stream()
                .map(ConversationMessage::id).toList());
        assertEquals("ui", messages.get(1).metadata().get("source"));
    }

    @Test
    void keysetCursorDoesNotSkipMessagesWithTheSameTimestamp() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Conversation conversation = new Conversation("conversation-1", tenant, null, "", 0, now, now);
        repository.save(conversation);
        repository.appendMessage(new ConversationMessage("message-1", tenant, conversation.id(),
                ConversationRole.USER, "one", Map.of(), now));
        repository.appendMessage(new ConversationMessage("message-2", tenant, conversation.id(),
                ConversationRole.USER, "two", Map.of(), now));
        repository.appendMessage(new ConversationMessage("message-3", tenant, conversation.id(),
                ConversationRole.USER, "three", Map.of(), now));

        List<ConversationMessage> first = repository.listMessagesAfter(tenant, conversation.id(), null, 2);
        List<ConversationMessage> second = repository.listMessagesAfter(tenant, conversation.id(),
                ConversationMessageCursor.after(first.get(1)), 2);

        assertEquals(List.of("message-3", "message-2"), first.stream()
                .map(ConversationMessage::id).toList());
        assertEquals(List.of("message-1"), second.stream().map(ConversationMessage::id).toList());
    }
}
