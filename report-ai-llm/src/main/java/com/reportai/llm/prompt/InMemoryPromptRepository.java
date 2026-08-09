package com.reportai.llm.prompt;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Development/test repository. Production implementations should use a transactional database. */
public final class InMemoryPromptRepository implements PromptRepository {
    private final ConcurrentMap<String, ConcurrentMap<Integer, PromptTemplate>> templates = new ConcurrentHashMap<>();

    @Override
    public Optional<PromptTemplate> find(String id, int version) {
        if (id == null || id.isBlank() || version <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.getOrDefault(id, new ConcurrentHashMap<>()).get(version));
    }

    @Override
    public Optional<PromptTemplate> findActive(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return templates.getOrDefault(id, new ConcurrentHashMap<>()).values().stream()
                .filter(PromptTemplate::active).max(java.util.Comparator.comparingInt(PromptTemplate::version));
    }

    @Override
    public void save(PromptTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("template must not be null");
        }
        templates.compute(template.id(), (id, versions) -> {
            ConcurrentMap<Integer, PromptTemplate> map = new ConcurrentHashMap<>();
            if (versions != null) {
                map.putAll(versions);
            }
            PromptTemplate previous = map.get(template.version());
            if (previous != null && !previous.equals(template)) {
                throw new IllegalStateException("prompt version already exists: " + template.id() + ":" + template.version());
            }
            if (template.active()) {
                map.replaceAll((version, existing) -> existing.active()
                        ? new PromptTemplate(existing.id(), existing.version(), existing.content(), false) : existing);
            }
            map.put(template.version(), template);
            return map;
        });
    }
}
