package com.reportai.llm.prompt;

import java.util.Map;

public interface PromptRenderer {
    String render(PromptTemplate template, Map<String, ?> variables);

    default String render(String promptId, Map<String, ?> variables) {
        throw new UnsupportedOperationException("renderer has no prompt repository");
    }
}
