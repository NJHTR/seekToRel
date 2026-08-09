package com.reportai.agent;

import java.time.Instant;
import java.util.Objects;

/** Stable keyset cursor for descending conversation history pagination. */
public record ConversationMessageCursor(Instant createdAt, String messageId, boolean timestampOnly) {
    public ConversationMessageCursor(Instant createdAt, String messageId) {
        this(createdAt, messageId, false);
    }

    public ConversationMessageCursor {
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!timestampOnly && (messageId == null || messageId.isBlank()
                || !messageId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            throw new IllegalArgumentException("messageId is invalid");
        }
        if (timestampOnly) {
            messageId = "";
        }
    }

    public static ConversationMessageCursor atTimestamp(Instant createdAt) {
        return new ConversationMessageCursor(createdAt, "", true);
    }

    public static ConversationMessageCursor after(ConversationMessage message) {
        Objects.requireNonNull(message, "message");
        return new ConversationMessageCursor(message.createdAt(), message.id(), false);
    }
}
