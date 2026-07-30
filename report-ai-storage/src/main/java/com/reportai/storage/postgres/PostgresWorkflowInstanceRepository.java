package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import com.reportai.workflow.WorkflowInstance;
import com.reportai.workflow.WorkflowInstanceRepository;
import com.reportai.workflow.WorkflowStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Tenant-scoped PostgreSQL adapter. A trusted authentication context is mandatory. */
public final class PostgresWorkflowInstanceRepository implements WorkflowInstanceRepository {
    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {
    };

    private static final String FIND_SQL = """
            SELECT id, definition_id, definition_version, task_id, current_node, status, state, version
            FROM workflow_instance
            WHERE tenant_id = :tenant_id AND id = :id
            """;

    private static final String FIND_BY_TASK_SQL = """
            SELECT id, definition_id, definition_version, task_id, current_node, status, state, version
            FROM workflow_instance
            WHERE tenant_id = :tenant_id AND task_id = :task_id
            """;

    private static final String INSERT_SQL = """
            INSERT INTO workflow_instance
                (id, tenant_id, task_id, definition_id, definition_version, current_node, status, state, version, created_at, updated_at)
            VALUES (:id, :tenant_id, :task_id, :definition_id, :definition_version, :current_node, :status,
                    CAST(:state AS jsonb), :version, :created_at, :updated_at)
            """;

    private static final String UPDATE_SQL = """
            UPDATE workflow_instance
            SET current_node = :current_node, status = :status, state = CAST(:state AS jsonb),
                version = :new_version, updated_at = :updated_at
            WHERE tenant_id = :tenant_id AND id = :id AND version = :expected_version
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresWorkflowInstanceRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkflowInstance> findById(String workflowId) {
        String tenantId = TenantContext.require().value();
        try {
            WorkflowInstance instance = jdbcTemplate.queryForObject(FIND_SQL,
                    new MapSqlParameterSource().addValue("tenant_id", tenantId).addValue("id", workflowId),
                    this::mapInstance);
            return Optional.ofNullable(instance);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkflowInstance> findByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "taskId is required");
        }
        String tenantId = TenantContext.require().value();
        try {
            WorkflowInstance instance = jdbcTemplate.queryForObject(FIND_BY_TASK_SQL,
                    new MapSqlParameterSource().addValue("tenant_id", tenantId).addValue("task_id", taskId),
                    this::mapInstance);
            return Optional.ofNullable(instance);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public WorkflowInstance create(WorkflowInstance instance) {
        if (instance.taskId() == null || instance.taskId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "workflow taskId is required");
        }
        String tenantId = TenantContext.require().value();
        Instant now = Instant.now();
        jdbcTemplate.update(INSERT_SQL, parameters(instance, tenantId, now));
        return instance;
    }

    @Override
    public WorkflowInstance update(WorkflowInstance instance, long expectedVersion) {
        String tenantId = TenantContext.require().value();
        int updated = jdbcTemplate.update(UPDATE_SQL,
                parameters(instance, tenantId, Instant.now())
                        .addValue("new_version", instance.version())
                        .addValue("expected_version", expectedVersion));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "workflow version conflict");
        }
        return instance;
    }

    private MapSqlParameterSource parameters(WorkflowInstance instance, String tenantId, Instant timestamp) {
        try {
            return new MapSqlParameterSource()
                    .addValue("id", instance.id())
                    .addValue("tenant_id", tenantId)
                    .addValue("task_id", instance.taskId())
                    .addValue("definition_id", instance.definitionId())
                    .addValue("definition_version", instance.definitionVersion())
                    .addValue("current_node", instance.currentNodeId())
                    .addValue("status", instance.status().name())
                    .addValue("state", objectMapper.writeValueAsString(instance.state()))
                    .addValue("version", instance.version())
                    .addValue("created_at", timestamp)
                    .addValue("updated_at", timestamp);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("workflow state serialization failed", exception);
        }
    }

    private WorkflowInstance mapInstance(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            Map<String, Object> state = objectMapper.readValue(resultSet.getString("state"), STATE_TYPE);
            return new WorkflowInstance(resultSet.getString("id"), resultSet.getString("definition_id"),
                    resultSet.getInt("definition_version"), resultSet.getString("task_id"), resultSet.getString("current_node"),
                    WorkflowStatus.valueOf(resultSet.getString("status")), state,
                    resultSet.getLong("version"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored workflow state is invalid", exception);
        }
    }
}
