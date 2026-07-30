package com.reportai.llm;

public record ChatMessage(Role role, String content) {
    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (content.length() > 1_000_000) {
            throw new IllegalArgumentException("content exceeds the maximum allowed length");
        }
    }

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }
}
