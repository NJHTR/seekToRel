package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationContextOrchestratorTest {
    @Test
    void compactsOlderHistoryAndKeepsChronologicalPromptOrder() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Conversation conversation = new Conversation("conversation-1", tenant, null, "Experiment", 0,
                created, created);
        repository.save(conversation);
        append(repository, tenant, conversation.id(), "message-1", ConversationRole.USER, "first", created, 1);
        append(repository, tenant, conversation.id(), "message-2", ConversationRole.ASSISTANT, "second", created, 2);
        append(repository, tenant, conversation.id(), "message-3", ConversationRole.USER, "third", created, 3);
        append(repository, tenant, conversation.id(), "message-4", ConversationRole.ASSISTANT, "fourth", created, 4);
        append(repository, tenant, conversation.id(), "message-5", ConversationRole.USER, "fifth", created, 5);

        ContextWindowPolicy policy = new ContextWindowPolicy(512, 128, 4, 1024, 512);
        ConversationContext context = new ConversationContextOrchestrator(repository).build(
                tenant, conversation.id(), policy);

        assertTrue(context.compacted());
        assertEquals(1, context.omittedMessageCount());
        assertEquals(ChatMessage.Role.USER, context.messages().get(0).role());
        assertEquals(ChatMessage.Role.ASSISTANT, context.messages().get(context.messages().size() - 2).role());
        assertEquals("fifth", context.messages().get(context.messages().size() - 1).content());
        assertTrue(context.estimatedTokens() <= policy.historyTokenBudget());
    }

    @Test
    void truncatesAPathologicallyLongLatestMessageToTheHistoryBudget() {
        InMemoryConversationRepository repository = new InMemoryConversationRepository();
        TenantId tenant = TenantId.of("tenant-a");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Conversation conversation = new Conversation("conversation-1", tenant, null, "", 0, now, now);
        repository.save(conversation);
        repository.appendMessage(new ConversationMessage("message-1", tenant, conversation.id(),
                ConversationRole.USER, "x".repeat(10_000), Map.of(), now));

        ContextWindowPolicy policy = new ContextWindowPolicy(128, 32, 10, 1024, 256);
        ConversationContext context = new ConversationContextOrchestrator(repository,
                new DeterministicConversationMemoryCompactor(), text -> TokenEstimator.approximate(text))
                .build(tenant, conversation.id(), policy);

        assertFalse(context.compacted());
        assertTrue(context.estimatedTokens() <= policy.historyTokenBudget());
        assertTrue(context.messages().get(0).content().length() <= 4 * policy.historyTokenBudget());
    }

    private static void append(InMemoryConversationRepository repository, TenantId tenant,
                               String conversationId, String id, ConversationRole role,
                               String content, Instant base, long seconds) {
        repository.appendMessage(new ConversationMessage(id, tenant, conversationId, role,
                content, Map.of(), base.plusSeconds(seconds)));
    }
}
