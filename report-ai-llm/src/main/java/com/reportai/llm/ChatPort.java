package com.reportai.llm;

public interface ChatPort {
    ChatResponse chat(ChatRequest request);
}
