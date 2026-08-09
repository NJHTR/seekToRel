package com.reportai.llm.prompt;

import java.util.Optional;

public interface PromptRepository {
    Optional<PromptTemplate> find(String id, int version);

    Optional<PromptTemplate> findActive(String id);

    void save(PromptTemplate template);
}
