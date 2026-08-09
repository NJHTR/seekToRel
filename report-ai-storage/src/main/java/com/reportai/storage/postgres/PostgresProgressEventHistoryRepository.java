package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventCursor;
import com.reportai.scheduler.ProgressEventHistoryRepository;
import com.reportai.scheduler.ProgressEventHistoryEntry;
import com.reportai.scheduler.ProgressEventType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** PostgreSQL append-only event history adapter with tenant and keyset predicates in SQL. */
public final class PostgresProgressEventHistoryRepository implements ProgressEventHistoryRepository {
    private static final TypeReference<Map<String, String>> ATTRIBUTES_TYPE = new TypeReference<>() {
    };
    private static final String INSERT_SQL = """
            INSERT INTO progress_event
                (tenant_id, event_id, task_id, attempt, event_type, percent, message, occurred_at, attributes)
            VALUES (:tenant_id, :event_id, :task_id, :attempt, :event_type, :percent, :message,
                    :occurred_at, CAST(:attributes AS jsonb))
            ON CONFLICT (tenant_id, event_id) DO NOTHING
            """;
    private static final String RETURNING_COLUMNS = """
            event_sequence, event_id, task_id, attempt, event_type, percent, message, occurred_at, attributes
            """;
    private static final String BASE_SELECT = """
            SELECT event_sequence, event_id, task_id, attempt, event_type, percent, message, occurred_at, attributes
            FROM progress_event
            WHERE tenant_id = :tenant_id AND task_id = :task_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresProgressEventHistoryRepository(NamedParameterJdbcTemplate jdbc,
                                                  ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ProgressEventHistoryEntry append(TenantId tenantId, ProgressEvent event) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(event, "event");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenant_id", tenantId.value())
                .addValue("event_id", event.eventId())
                .addValue("task_id", event.taskId())
                .addValue("attempt", event.attempt())
                .addValue("event_type", event.type().name())
                .addValue("percent", event.percent())
                .addValue("message", event.message())
                .addValue("occurred_at", Timestamp.from(event.occurredAt()))
                .addValue("attributes", writeAttributes(event.attributes()));
        List<ProgressEventHistoryEntry> inserted = jdbc.query(
                INSERT_SQL + " RETURNING " + RETURNING_COLUMNS, parameters, this::mapEvent);
        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }
        return jdbc.queryForObject("""
                SELECT event_sequence, event_id, task_id, attempt, event_type, percent, message,
                       occurred_at, attributes
                FROM progress_event
                WHERE tenant_id = :tenant_id AND event_id = :event_id
                """, new MapSqlParameterSource().addValue("tenant_id", tenantId.value())
                .addValue("event_id", event.eventId()), this::mapEvent);
    }

    @Override
    public List<ProgressEventHistoryEntry> listAfter(TenantId tenantId, String taskId,
                                                     ProgressEventCursor after, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (taskId == null || !taskId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("taskId is invalid");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenant_id", tenantId.value())
                .addValue("task_id", taskId)
                .addValue("limit", limit);
        String sql = BASE_SELECT;
        if (after != null) {
            sql += " AND event_sequence > :after_event_sequence\n";
            parameters.addValue("after_event_sequence", after.eventSequence());
        }
        sql += " ORDER BY event_sequence ASC LIMIT :limit";
        return jdbc.query(sql, parameters, this::mapEvent);
    }

    private String writeAttributes(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(ProgressEvent.sanitizeAttributes(attributes));
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("progress event attributes cannot be serialized", failure);
        }
    }

    private ProgressEventHistoryEntry mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            Map<String, String> attributes = resultSet.getString("attributes") == null
                    ? Map.of() : objectMapper.readValue(resultSet.getString("attributes"), ATTRIBUTES_TYPE);
            long sequence = resultSet.getLong("event_sequence");
            ProgressEvent event = new ProgressEvent(resultSet.getString("event_id"), resultSet.getString("task_id"),
                    resultSet.getInt("attempt"), ProgressEventType.valueOf(resultSet.getString("event_type")),
                    resultSet.getInt("percent"), resultSet.getString("message"),
                    resultSet.getTimestamp("occurred_at").toInstant(), attributes, sequence);
            return new ProgressEventHistoryEntry(sequence, event);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new SQLException("progress event is invalid", failure);
        }
    }
}
