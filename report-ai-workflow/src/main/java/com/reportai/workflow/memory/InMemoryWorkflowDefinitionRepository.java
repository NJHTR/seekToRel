package com.reportai.workflow.memory;

import com.reportai.workflow.WorkflowDefinition;
import com.reportai.workflow.WorkflowDefinitionRepository;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Development/test adapter. Production should implement the same port with PostgreSQL. */
public final class InMemoryWorkflowDefinitionRepository implements WorkflowDefinitionRepository {
    private final Map<String, Map<Integer, WorkflowDefinition>> definitions = new ConcurrentHashMap<>();

    @Override
    public Optional<WorkflowDefinition> findLatest(String definitionId) {
        Map<Integer, WorkflowDefinition> versions = definitions.get(definitionId);
        if (versions == null) {
            return Optional.empty();
        }
        return versions.values().stream().max(Comparator.comparingInt(WorkflowDefinition::version));
    }

    @Override
    public Optional<WorkflowDefinition> find(String definitionId, int version) {
        Map<Integer, WorkflowDefinition> versions = definitions.get(definitionId);
        return versions == null ? Optional.empty() : Optional.ofNullable(versions.get(version));
    }

    @Override
    public WorkflowDefinition save(WorkflowDefinition definition) {
        definitions.computeIfAbsent(definition.id(), ignored -> new ConcurrentHashMap<>())
                .compute(definition.version(), (version, existing) -> {
                    if (existing != null) {
                        throw new IllegalStateException("workflow definition version already exists");
                    }
                    return definition;
                });
        return definition;
    }
}
