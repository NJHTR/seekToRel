package com.reportai.agent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Development/test adapter. Production adapters should use a unique key and conditional update. */
public final class InMemoryAgentPipelineRepository implements AgentPipelineRepository {
    private final ConcurrentMap<String, AgentPipelineCheckpoint> checkpoints = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentPipelineCheckpoint> findById(String pipelineId) {
        return Optional.ofNullable(checkpoints.get(pipelineId));
    }

    @Override
    public AgentPipelineCheckpoint create(AgentPipelineCheckpoint checkpoint) {
        if (checkpoints.putIfAbsent(checkpoint.pipelineId(), checkpoint) != null) {
            throw new IllegalStateException("agent pipeline already exists: " + checkpoint.pipelineId());
        }
        return checkpoint;
    }

    @Override
    public AgentPipelineCheckpoint update(AgentPipelineCheckpoint checkpoint, long expectedVersion) {
        final AgentPipelineCheckpoint[] updated = new AgentPipelineCheckpoint[1];
        checkpoints.compute(checkpoint.pipelineId(), (ignored, current) -> {
            if (current == null || current.version() != expectedVersion) {
                return current;
            }
            updated[0] = checkpoint;
            return checkpoint;
        });
        if (updated[0] == null) {
            throw new IllegalStateException("agent pipeline version conflict: " + checkpoint.pipelineId());
        }
        return updated[0];
    }
}
