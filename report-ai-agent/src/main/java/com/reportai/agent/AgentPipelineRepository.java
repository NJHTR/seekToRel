package com.reportai.agent;

import java.util.Optional;

/** Persistence boundary for resumable agent cursors. Implementations must CAS on version. */
public interface AgentPipelineRepository {
    Optional<AgentPipelineCheckpoint> findById(String pipelineId);

    AgentPipelineCheckpoint create(AgentPipelineCheckpoint checkpoint);

    AgentPipelineCheckpoint update(AgentPipelineCheckpoint checkpoint, long expectedVersion);
}
