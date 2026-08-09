package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.ChatMessage;
import com.reportai.llm.TokenEstimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a bounded chronological prompt context from append-only conversation
 * history. The repository remains tenant-scoped and the compactor is an
 * injectable port, so an LLM is optional and never required for local tests.
 */
public final class ConversationContextOrchestrator {
    private static final int MAX_REPOSITORY_PAGE = 1_000;
    private static final int CHARS_PER_APPROXIMATE_TOKEN = 4;

    private final ConversationRepository repository;
    private final ConversationMemoryCompactor compactor;
    private final TokenEstimator tokenEstimator;
    private final ConversationMemorySnapshotRepository snapshots;

    public ConversationContextOrchestrator(ConversationRepository repository) {
        this(repository, new DeterministicConversationMemoryCompactor(), TokenEstimator::approximate, null);
    }

    public ConversationContextOrchestrator(ConversationRepository repository,
                                           ConversationMemoryCompactor compactor,
                                           TokenEstimator tokenEstimator) {
        this(repository, compactor, tokenEstimator, null);
    }

    public ConversationContextOrchestrator(ConversationRepository repository,
                                           ConversationMemoryCompactor compactor,
                                           TokenEstimator tokenEstimator,
                                           ConversationMemorySnapshotRepository snapshots) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.compactor = Objects.requireNonNull(compactor, "compactor");
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
        this.snapshots = snapshots;
    }

    public ConversationContext build(TenantId tenantId, String conversationId,
                                     ContextWindowPolicy policy) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        Objects.requireNonNull(policy, "policy");
        if (repository.findById(tenantId, conversationId).isEmpty()) {
            return new ConversationContext(List.of(), 0, 0, false, "");
        }

        int requested = Math.min(MAX_REPOSITORY_PAGE, policy.maxMessages() + 1);
        List<ConversationMessage> newestFirst = repository.listMessagesAfter(tenantId, conversationId,
                null, requested);
        if (newestFirst.isEmpty()) {
            return new ConversationContext(List.of(), 0, 0, false, "");
        }

        ConversationMemorySnapshot snapshot = snapshots == null
                ? null : snapshots.find(tenantId, conversationId).orElse(null);
        List<ConversationMessage> postSnapshot = newestFirst.stream()
                .filter(message -> snapshot == null || isNewerThan(message, snapshot.through()))
                .toList();
        int retainedCount = Math.min(policy.maxMessages(), postSnapshot.size());
        List<ConversationMessage> recent = new ArrayList<>(postSnapshot.subList(0, retainedCount));
        List<ConversationMessage> omitted = new ArrayList<>();
        if (postSnapshot.size() > retainedCount) {
            omitted.addAll(postSnapshot.subList(retainedCount, postSnapshot.size()));
        }

        List<ConversationMessage> selectedNewestFirst = selectWithinBudget(recent, policy);
        Set<String> selectedIds = selectedNewestFirst.stream()
                .map(ConversationMessage::id)
                .collect(java.util.stream.Collectors.toSet());
        for (ConversationMessage message : recent) {
            if (!selectedIds.contains(message.id())) {
                omitted.add(message);
            }
        }
        String summary = snapshot == null ? "" : snapshot.summary();
        String freshSummary = compact(tenantId, conversationId, omitted, policy.maxSummaryChars());
        if (!freshSummary.isBlank()) {
            summary = summary.isBlank() ? freshSummary : summary + "\n\n" + freshSummary;
        }

        // Adding a summary consumes the same input budget as ordinary history.
        // If it does not fit, move the oldest selected messages into the omitted
        // set and rebuild the summary once; this keeps the result deterministic.
        if (!summary.isBlank()) {
            int summaryTokens = tokenEstimator.estimate(summary);
            while (!selectedNewestFirst.isEmpty()
                    && summaryTokens + estimateConversationMessages(selectedNewestFirst) > policy.historyTokenBudget()
                    && selectedNewestFirst.size() > 1) {
                ConversationMessage removed = selectedNewestFirst.remove(selectedNewestFirst.size() - 1);
                omitted.add(removed);
                freshSummary = compact(tenantId, conversationId, omitted, policy.maxSummaryChars());
                summary = snapshot == null || snapshot.summary().isBlank()
                        ? freshSummary : snapshot.summary() + "\n\n" + freshSummary;
                summaryTokens = tokenEstimator.estimate(summary);
            }
            int remainingTokens = policy.historyTokenBudget() - estimateConversationMessages(selectedNewestFirst);
            if (remainingTokens <= 0) {
                summary = "";
            } else if (summaryTokens > remainingTokens) {
                summary = truncate(summary, remainingTokens);
            }
        }

        List<ChatMessage> messages = new ArrayList<>();
        if (!summary.isBlank()) {
            // Memory contains excerpts of user-controlled text. Keep it at
            // user/data priority instead of granting it system authority.
            messages.add(new ChatMessage(ChatMessage.Role.USER, summary));
        }
        List<ConversationMessage> chronological = new ArrayList<>(selectedNewestFirst);
        Collections.reverse(chronological);
        for (ConversationMessage message : chronological) {
            messages.add(new ChatMessage(toRole(message.role()), boundedContent(message.content(), policy.maxMessageChars())));
        }
        return new ConversationContext(messages, estimateChatMessages(messages), omitted.size(),
                !summary.isBlank(), summary);
    }

    private List<ConversationMessage> selectWithinBudget(List<ConversationMessage> recent,
                                                         ContextWindowPolicy policy) {
        List<ConversationMessage> selected = new ArrayList<>();
        int budget = policy.historyTokenBudget();
        int used = 0;
        for (ConversationMessage original : recent) {
            ConversationMessage bounded = boundedMessage(original, policy.maxMessageChars());
            int cost = tokenEstimator.estimate(bounded.content());
            if (selected.isEmpty() && cost > budget) {
                bounded = boundedMessage(original, Math.max(1, budget * CHARS_PER_APPROXIMATE_TOKEN));
                cost = tokenEstimator.estimate(bounded.content());
            }
            if (used + cost <= budget || selected.isEmpty()) {
                selected.add(bounded);
                used = Math.min(Integer.MAX_VALUE - cost, used + cost);
            }
            if (used >= budget) {
                break;
            }
        }
        return selected;
    }

    private String compact(TenantId tenantId, String conversationId,
                           List<ConversationMessage> omitted, int maxChars) {
        if (omitted.isEmpty()) {
            return "";
        }
        try {
            String value = compactor.compact(tenantId, conversationId, List.copyOf(omitted), maxChars);
            return value == null ? "" : value.trim();
        } catch (RuntimeException ignored) {
            // Context assembly must fail closed when an optional summarizer is
            // unavailable; sending less history is safer than failing the task
            // or exposing provider details through an agent error.
            return "";
        }
    }

    private int estimateConversationMessages(List<ConversationMessage> messages) {
        long total = 0;
        for (ConversationMessage message : messages) {
            total += tokenEstimator.estimate(message.content());
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    private int estimateChatMessages(List<ChatMessage> messages) {
        long total = 0;
        for (ChatMessage message : messages) {
            total += tokenEstimator.estimate(message.content());
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) total;
    }

    private static ConversationMessage boundedMessage(ConversationMessage message, int maxChars) {
        return new ConversationMessage(message.id(), message.tenantId(), message.conversationId(), message.role(),
                boundedContent(message.content(), maxChars), message.metadata(), message.createdAt());
    }

    private static String boundedContent(String content, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        if (maxChars <= 16) {
            return content.substring(0, maxChars);
        }
        String marker = "\n...[truncated]...\n";
        int available = maxChars - marker.length();
        int head = available / 2;
        int tail = available - head;
        return content.substring(0, head) + marker + content.substring(content.length() - tail);
    }

    private static String truncate(String value, int tokenBudget) {
        int maxChars = Math.max(1, tokenBudget * CHARS_PER_APPROXIMATE_TOKEN);
        return boundedContent(value, maxChars);
    }

    private static ChatMessage.Role toRole(ConversationRole role) {
        return switch (role) {
            case SYSTEM -> ChatMessage.Role.SYSTEM;
            case USER -> ChatMessage.Role.USER;
            case ASSISTANT -> ChatMessage.Role.ASSISTANT;
            case TOOL -> ChatMessage.Role.TOOL;
        };
    }

    private static boolean isNewerThan(ConversationMessage message, ConversationMessageCursor cursor) {
        int time = message.createdAt().compareTo(cursor.createdAt());
        return time > 0 || (time == 0 && message.id().compareTo(cursor.messageId()) > 0);
    }
}
