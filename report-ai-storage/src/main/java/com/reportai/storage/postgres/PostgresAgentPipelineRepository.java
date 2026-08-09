package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.agent.AgentPipelineCheckpoint;
import com.reportai.agent.AgentPipelineRepository;
import com.reportai.agent.AgentPipelineStatus;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tenant-scoped PostgreSQL persistence for resumable agent cursors. */
public final class PostgresAgentPipelineRepository implements AgentPipelineRepository {
    private static final TypeReference<List<String>> TYPES = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> STATE = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresAgentPipelineRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<AgentPipelineCheckpoint> findById(String pipelineId) {
        requireId(pipelineId);
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT pipeline_id, tenant_id, task_id, agent_types, next_agent_index,
                           status, state, message, version, updated_at
                    FROM agent_pipeline_checkpoint
                    WHERE tenant_id=:tenant_id AND pipeline_id=:pipeline_id
                    """, params(pipelineId), this::map));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public AgentPipelineCheckpoint create(AgentPipelineCheckpoint checkpoint) {
        requireTenant(checkpoint);
        try {
            jdbc.update("""
                    INSERT INTO agent_pipeline_checkpoint
                        (pipeline_id, tenant_id, task_id, agent_types, next_agent_index, status,
                         state, message, version, updated_at)
                    VALUES (:pipeline_id, :tenant_id, :task_id, CAST(:agent_types AS jsonb), :next_agent_index,
                            :status, CAST(:state AS jsonb), :message, :version, :updated_at)
                    """, params(checkpoint));
            return checkpoint;
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.CONFLICT, "agent pipeline already exists");
        }
    }

    @Override
    public AgentPipelineCheckpoint update(AgentPipelineCheckpoint checkpoint, long expectedVersion) {
        requireTenant(checkpoint);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        int count = jdbc.update("""
                UPDATE agent_pipeline_checkpoint
                SET next_agent_index=:next_agent_index, status=:status, state=CAST(:state AS jsonb),
                    message=:message, version=:new_version, updated_at=:updated_at
                WHERE tenant_id=:tenant_id AND pipeline_id=:pipeline_id AND version=:expected_version
                """, params(checkpoint).addValue("new_version", checkpoint.version())
                .addValue("expected_version", expectedVersion));
        if (count != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "agent pipeline version conflict");
        }
        return checkpoint;
    }

    private MapSqlParameterSource params(String pipelineId) {
        return new MapSqlParameterSource().addValue("tenant_id", TenantContext.require().value())
                .addValue("pipeline_id", pipelineId);
    }

    private MapSqlParameterSource params(AgentPipelineCheckpoint checkpoint) {
        try {
            return new MapSqlParameterSource().addValue("tenant_id", TenantContext.require().value())
                    .addValue("pipeline_id", checkpoint.pipelineId()).addValue("task_id", checkpoint.taskId())
                    .addValue("agent_types", objectMapper.writeValueAsString(checkpoint.agentTypes()))
                    .addValue("next_agent_index", checkpoint.nextAgentIndex()).addValue("status", checkpoint.status().name())
                    .addValue("state", objectMapper.writeValueAsString(checkpoint.state()))
                    .addValue("message", checkpoint.message()).addValue("version", checkpoint.version())
                    .addValue("updated_at", Timestamp.from(checkpoint.updatedAt()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("agent pipeline state serialization failed", failure);
        }
    }

    private AgentPipelineCheckpoint map(ResultSet rs, int row) throws SQLException {
        try {
            List<String> types = objectMapper.readValue(rs.getString("agent_types"), TYPES);
            Map<String, Object> state = objectMapper.readValue(rs.getString("state"), STATE);
            return new AgentPipelineCheckpoint(rs.getString("pipeline_id"), rs.getString("task_id"),
                    new com.reportai.common.tenant.TenantId(rs.getString("tenant_id")), types,
                    rs.getInt("next_agent_index"), AgentPipelineStatus.valueOf(rs.getString("status")), state,
                    rs.getString("message"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new SQLException("stored agent pipeline checkpoint is invalid", failure);
        }
    }

    private static void requireId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank() || pipelineId.length() > 128) {
            throw new IllegalArgumentException("pipelineId is invalid");
        }
    }

    private static void requireTenant(AgentPipelineCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint is required");
        }
        String currentTenant = TenantContext.require().value();
        if (!currentTenant.equals(checkpoint.tenantId().value())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "agent pipeline tenant mismatch");
        }
    }
}
