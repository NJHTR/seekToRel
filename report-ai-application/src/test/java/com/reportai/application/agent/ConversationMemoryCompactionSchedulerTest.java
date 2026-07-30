package com.reportai.application.agent;

import com.reportai.agent.Conversation;
import com.reportai.agent.ConversationCompactionCandidate;
import com.reportai.agent.ConversationMemoryCompactor;
import com.reportai.agent.ConversationMemorySnapshotService;
import com.reportai.agent.ConversationRole;
import com.reportai.agent.DeterministicConversationMemoryCompactor;
import com.reportai.agent.InMemoryConversationMemorySnapshotRepository;
import com.reportai.agent.InMemoryConversationRepository;
import com.reportai.application.config.properties.ReportAiMemorySnapshotProperties;
import com.reportai.common.tenant.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationMemoryCompactionSchedulerTest {
    @Test
    void retriesTransientCompactionFailureAndRecordsSuccess() {
        TenantId tenant = TenantId.of("tenant-a");
        String conversationId = "conversation-1";
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        InMemoryConversationRepository conversations = new InMemoryConversationRepository();
        conversations.save(new Conversation(conversationId, tenant, null, "", 0, now, now));
        conversations.appendMessage(new com.reportai.agent.ConversationMessage("message-1", tenant, conversationId,
                ConversationRole.USER, "hello", Map.of(), now));

        AtomicInteger attempts = new AtomicInteger();
        ConversationMemoryCompactor flaky = (candidateTenant, candidateConversation, messages, maxChars) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary provider failure");
            }
            return new DeterministicConversationMemoryCompactor().compact(candidateTenant, candidateConversation,
                    messages, maxChars);
        };
        ConversationMemorySnapshotService service = new ConversationMemorySnapshotService(conversations,
                new InMemoryConversationMemorySnapshotRepository(), flaky);
        ReportAiMemorySnapshotProperties properties = properties();
        try (ConversationMemoryCompactionScheduler scheduler = new ConversationMemoryCompactionScheduler(
                ignored -> List.of(new ConversationCompactionCandidate(tenant, conversationId)), service,
                properties, new SimpleMeterRegistry())) {
            ConversationMemoryCompactionScheduler.CompactionCycleResult result = scheduler.compactNow();
            assertEquals(1, result.selected());
            assertEquals(1, result.succeeded());
            assertEquals(2, result.retries());
            assertEquals(0, result.failures());
            assertEquals(3, attempts.get());
        }
    }

    private static ReportAiMemorySnapshotProperties properties() {
        ReportAiMemorySnapshotProperties properties = new ReportAiMemorySnapshotProperties();
        properties.setInterval(java.time.Duration.ofMinutes(1));
        properties.setInitialDelay(java.time.Duration.ZERO);
        properties.setBatchSize(10);
        properties.setMaxSummaryChars(1024);
        properties.setMaxAttempts(3);
        return properties;
    }
}
