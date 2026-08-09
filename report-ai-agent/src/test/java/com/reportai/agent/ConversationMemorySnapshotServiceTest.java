package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemorySnapshotServiceTest {
    @Test
    void createsCasProtectedSnapshotAndContextReusesIt() {
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        InMemoryConversationMemorySnapshotRepository snapshots = new InMemoryConversationMemorySnapshotRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Conversation conversation = new Conversation("conversation-1", tenant, null, "", 0, now, now);
        conversations.save(conversation);
        append(conversations, tenant, conversation.id(), "message-1", "one", now, 1);
        append(conversations, tenant, conversation.id(), "message-2", "two", now, 2);

        ConversationMemorySnapshotService service = new ConversationMemorySnapshotService(
                conversations, snapshots, new DeterministicConversationMemoryCompactor(),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC));
        ConversationMemorySnapshot snapshot = service.compactNow(tenant, conversation.id(), 1024).orElseThrow();

        assertEquals(0, snapshot.version());
        assertEquals("message-1", snapshot.through().messageId());
        assertTrue(snapshot.summary().contains("one"));

        ConversationContext context = new ConversationContextOrchestrator(conversations,
                new DeterministicConversationMemoryCompactor(), text -> (text.length() + 3) / 4, snapshots)
                .build(tenant, conversation.id(), new ContextWindowPolicy(512, 128, 10, 1024, 1024));
        assertTrue(context.compacted());
        assertEquals(2, context.messages().size());
        assertTrue(context.messages().get(0).content().contains("one"));
        assertEquals("two", context.messages().get(1).content());
    }

    @Test
    void snapshotRepositoryEnforcesTenantAndCasIsolation() {
        InMemoryConversationMemorySnapshotRepository repository = new InMemoryConversationMemorySnapshotRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ConversationMemorySnapshot snapshot = new ConversationMemorySnapshot(tenant, "conversation-1",
                new ConversationMessageCursor(now, "message-1"), "summary", 0, now, now);
        repository.save(snapshot);

        ConversationMemorySnapshot next = new ConversationMemorySnapshot(tenant, "conversation-1",
                new ConversationMessageCursor(now, "message-2"), "next", 1, now, now.plusSeconds(1));
        assertTrue(repository.update(next, 0));
        assertTrue(repository.find(TenantId.of("tenant-b"), "conversation-1").isEmpty());
        assertFalse(repository.update(snapshot, 0));
    }

    private static void append(InMemoryConversationRepository repository, TenantId tenant,
                               String conversationId, String id, String content, Instant base, long seconds) {
        repository.appendMessage(new ConversationMessage(id, tenant, conversationId,
                ConversationRole.USER, content, Map.of(), base.plusSeconds(seconds)));
    }
}
