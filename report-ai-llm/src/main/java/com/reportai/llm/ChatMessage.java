package com.reportai.llm;

import java.util.List;

public record ChatMessage(Role role, String content, List<ChatContentPart> contentParts) {
    public ChatMessage(Role role, String content) {
        this(role, content, List.of());
    }

    public static ChatMessage multimodal(Role role, String text, List<ChatContentPart> parts) {
        return new ChatMessage(role, text, parts);
    }

    public ChatMessage {
        contentParts = contentParts == null ? List.of() : List.copyOf(contentParts);
        validate(role, content, contentParts);
    }

    private static void validate(Role role, String content, List<ChatContentPart> contentParts) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if ((content == null || content.isBlank()) && contentParts.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (content != null && content.length() > 1_000_000) {
            throw new IllegalArgumentException("content exceeds the maximum allowed length");
        }
        if (contentParts.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("contentParts must not contain null values");
        }
    }

    public boolean isMultimodal() {
        return !contentParts.isEmpty();
    }

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }
}
