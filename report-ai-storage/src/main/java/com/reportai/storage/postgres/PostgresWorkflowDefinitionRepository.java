package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import com.reportai.workflow.WorkflowDefinition;
import com.reportai.workflow.WorkflowDefinitionRepository;
import com.reportai.workflow.WorkflowNode;
import com.reportai.workflow.WorkflowTransition;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PostgreSQL adapter for immutable, tenant-scoped workflow definitions.
 *
 * <p>Definitions are append-only: a definition id can have many versions,
 * but a given tenant/id/version can only be stored once.</p>
 */
public final class PostgresWorkflowDefinitionRepository implements WorkflowDefinitionRepository {
    private static final TypeReference<Map<String, WorkflowNode>> NODES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Set<WorkflowTransition>> TRANSITIONS_TYPE = new TypeReference<>() {
    };

    private static final String FIND_LATEST_SQL = """
            SELECT definition_id, version, start_node_id, nodes, transitions
            FROM workflow_definition
            WHERE tenant_id = :tenant_id AND definition_id = :definition_id
            ORDER BY version DESC
            LIMIT 1
            """;

    private static final String FIND_VERSION_SQL = """
            SELECT definition_id, version, start_node_id, nodes, transitions
            FROM workflow_definition
            WHERE tenant_id = :tenant_id AND definition_id = :definition_id AND version = :version
            """;

    private static final String INSERT_SQL = """
            INSERT INTO workflow_definition
                (tenant_id, definition_id, version, start_node_id, nodes, transitions, created_at)
            VALUES (:tenant_id, :definition_id, :version, :start_node_id,
                    CAST(:nodes AS jsonb), CAST(:transitions AS jsonb), :created_at)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresWorkflowDefinitionRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkflowDefinition> findLatest(String definitionId) {
        String tenantId = TenantContext.require().value();
        try {
            WorkflowDefinition definition = jdbcTemplate.queryForObject(
                    FIND_LATEST_SQL,
                    new MapSqlParameterSource()
                            .addValue("tenant_id", tenantId)
                            .addValue("definition_id", definitionId),
                    this::mapDefinition);
            return Optional.ofNullable(definition);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<WorkflowDefinition> find(String definitionId, int version) {
        if (version < 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "workflow definition version must be positive");
        }
        String tenantId = TenantContext.require().value();
        try {
            WorkflowDefinition definition = jdbcTemplate.queryForObject(
                    FIND_VERSION_SQL,
                    new MapSqlParameterSource()
                            .addValue("tenant_id", tenantId)
                            .addValue("definition_id", definitionId)
                            .addValue("version", version),
                    this::mapDefinition);
            return Optional.ofNullable(definition);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public WorkflowDefinition save(WorkflowDefinition definition) {
        String tenantId = TenantContext.require().value();
        try {
            jdbcTemplate.update(INSERT_SQL, parameters(definition, tenantId));
            return definition;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "workflow definition version already exists");
        }
    }

    private MapSqlParameterSource parameters(WorkflowDefinition definition, String tenantId) {
        try {
            return new MapSqlParameterSource()
                    .addValue("tenant_id", tenantId)
                    .addValue("definition_id", definition.id())
                    .addValue("version", definition.version())
                    .addValue("start_node_id", definition.startNodeId())
                    .addValue("nodes", objectMapper.writeValueAsString(definition.nodes()))
                    .addValue("transitions", objectMapper.writeValueAsString(definition.transitions()))
                    .addValue("created_at", Timestamp.from(Instant.now()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("workflow definition serialization failed", exception);
        }
    }

    private WorkflowDefinition mapDefinition(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            Map<String, WorkflowNode> nodes = objectMapper.readValue(resultSet.getString("nodes"), NODES_TYPE);
            Set<WorkflowTransition> transitions = objectMapper.readValue(
                    resultSet.getString("transitions"), TRANSITIONS_TYPE);
            return new WorkflowDefinition(
                    resultSet.getString("definition_id"),
                    resultSet.getInt("version"),
                    resultSet.getString("start_node_id"),
                    nodes,
                    transitions);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("stored workflow definition is invalid", exception);
        }
    }
}
