package com.reportai.llm;

public interface ToolCallPort {
    ToolCallResponse toolCall(ToolCallRequest request);
}
