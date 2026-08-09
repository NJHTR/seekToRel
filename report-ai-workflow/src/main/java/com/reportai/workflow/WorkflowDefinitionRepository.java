package com.reportai.workflow;

import java.util.Optional;

/** Persistence port for immutable, versioned workflow definitions. */
public interface WorkflowDefinitionRepository {
    Optional<WorkflowDefinition> findLatest(String definitionId);

    Optional<WorkflowDefinition> find(String definitionId, int version);

    WorkflowDefinition save(WorkflowDefinition definition);
}
