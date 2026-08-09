package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.workflow.WorkflowAction;
import com.reportai.workflow.WorkflowEvent;
import com.reportai.workflow.WorkflowEventRepository;
import com.reportai.workflow.WorkflowStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PostgresWorkflowEventRepository implements WorkflowEventRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresWorkflowEventRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void append(WorkflowEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO workflow_event (id, workflow_id, action, from_status, to_status, version, metadata, occurred_at)
                    VALUES (:id, :workflow_id, :action, :from_status, :to_status, :version, CAST(:metadata AS jsonb), :occurred_at)
                    """, new MapSqlParameterSource().addValue("id", event.id())
                    .addValue("workflow_id", event.workflowId()).addValue("action", event.action().name())
                    .addValue("from_status", event.fromStatus().name()).addValue("to_status", event.toStatus().name())
                    .addValue("version", event.version()).addValue("metadata", objectMapper.writeValueAsString(event.metadata()))
                    .addValue("occurred_at", Timestamp.from(event.occurredAt())));
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("workflow event version already exists", duplicate);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("workflow event metadata serialization failed", failure);
        }
    }

    @Override
    public List<WorkflowEvent> list(String workflowId, long afterVersion, int limit) {
        if (workflowId == null || workflowId.isBlank() || afterVersion < -1 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("invalid workflow event query");
        }
        return jdbc.query("""
                SELECT id, workflow_id, action, from_status, to_status, version, metadata, occurred_at
                FROM workflow_event WHERE workflow_id=:workflow_id AND version > :after_version
                ORDER BY version ASC LIMIT :limit
                """, new MapSqlParameterSource().addValue("workflow_id", workflowId)
                .addValue("after_version", afterVersion).addValue("limit", limit),
                this::map);
    }

    private WorkflowEvent map(ResultSet rs, int row) throws SQLException {
        try {
            Map<String, Object> metadata = objectMapper.readValue(rs.getString("metadata"), MAP_TYPE);
            return new WorkflowEvent(rs.getString("id"), rs.getString("workflow_id"),
                    WorkflowAction.valueOf(rs.getString("action")), WorkflowStatus.valueOf(rs.getString("from_status")),
                    WorkflowStatus.valueOf(rs.getString("to_status")), rs.getLong("version"),
                    rs.getTimestamp("occurred_at").toInstant(), metadata);
        } catch (JsonProcessingException failure) {
            throw new SQLException("workflow event metadata is invalid", failure);
        }
    }
}
