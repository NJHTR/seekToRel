package com.reportai.agent;

import com.reportai.llm.ChatMessage;

import java.util.List;

/** Bounded, chronological prompt context assembled from a tenant conversation. */
public record ConversationContext(List<ChatMessage> messages, int estimatedTokens,
                                  int omittedMessageCount, boolean compacted,
                                  String summary) {
    public ConversationContext {
        if (estimatedTokens < 0 || omittedMessageCount < 0) {
            throw new IllegalArgumentException("context counters must not be negative");
        }
        messages = messages == null ? List.of() : List.copyOf(messages);
        summary = summary == null ? "" : summary;
        if (compacted != !summary.isBlank()) {
            throw new IllegalArgumentException("compacted flag must match summary presence");
        }
    }
}
