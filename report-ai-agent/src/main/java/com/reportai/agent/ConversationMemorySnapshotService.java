package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates a durable, CAS-protected memory summary without deleting messages.
 * The operation is deliberately bounded so a maliciously large conversation
 * cannot monopolize a worker.
 */
public final class ConversationMemorySnapshotService {
    private static final int PAGE_SIZE = 1_000;
    private static final int MAX_MESSAGES_PER_COMPACTION = 100_000;

    private final ConversationRepository conversations;
    private final ConversationMemorySnapshotRepository snapshots;
    private final ConversationMemoryCompactor compactor;
    private final Clock clock;

    public ConversationMemorySnapshotService(ConversationRepository conversations,
                                              ConversationMemorySnapshotRepository snapshots,
                                              ConversationMemoryCompactor compactor) {
        this(conversations, snapshots, compactor, Clock.systemUTC());
    }

    ConversationMemorySnapshotService(ConversationRepository conversations,
                                       ConversationMemorySnapshotRepository snapshots,
                                       ConversationMemoryCompactor compactor, Clock clock) {
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.compactor = Objects.requireNonNull(compactor, "compactor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<ConversationMemorySnapshot> compactNow(TenantId tenantId, String conversationId,
                                                            int maxSummaryChars) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (maxSummaryChars < 128 || maxSummaryChars > 128 * 1024) {
            throw new IllegalArgumentException("maxSummaryChars is outside the permitted range");
        }
        if (conversations.findById(tenantId, conversationId).isEmpty()) {
            return Optional.empty();
        }

        ConversationMemorySnapshot existing = snapshots.find(tenantId, conversationId).orElse(null);
        List<ConversationMessage> newestFirst = readBoundedHistory(tenantId, conversationId);
        if (newestFirst.isEmpty()) {
            return Optional.ofNullable(existing);
        }

        List<ConversationMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        if (existing != null) {
            chronological.add(0, new ConversationMessage("memory-summary", tenantId, conversationId,
                    ConversationRole.SYSTEM, existing.summary(), java.util.Map.of("memory", "snapshot"),
                    existing.updatedAt()));
        }
        String summary = compactor.compact(tenantId, conversationId, chronological, maxSummaryChars);
        if (summary == null || summary.isBlank()) {
            return Optional.ofNullable(existing);
        }
        ConversationMessage oldest = newestFirst.get(newestFirst.size() - 1);
        ConversationMessageCursor through = ConversationMessageCursor.after(oldest);
        Instant now = clock.instant();
        ConversationMemorySnapshot next = new ConversationMemorySnapshot(tenantId, conversationId, through,
                summary, existing == null ? 0 : existing.version() + 1,
                existing == null ? now : existing.createdAt(), now);
        if (existing == null) {
            return Optional.of(snapshots.save(next));
        }
        if (!snapshots.update(next, existing.version())) {
            throw new IllegalStateException("conversation memory snapshot changed concurrently");
        }
        return Optional.of(next);
    }

    private List<ConversationMessage> readBoundedHistory(TenantId tenantId, String conversationId) {
        List<ConversationMessage> result = new ArrayList<>();
        ConversationMessageCursor cursor = null;
        while (result.size() < MAX_MESSAGES_PER_COMPACTION) {
            int limit = Math.min(PAGE_SIZE, MAX_MESSAGES_PER_COMPACTION - result.size());
            List<ConversationMessage> page = conversations.listMessagesAfter(tenantId, conversationId, cursor, limit);
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
            cursor = ConversationMessageCursor.after(page.get(page.size() - 1));
            if (page.size() < limit) {
                break;
            }
        }
        return List.copyOf(result);
    }
}
