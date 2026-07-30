package com.reportai.storage.postgres;

import com.reportai.common.tenant.TenantId;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class PostgresReportTaskRepository implements ReportTaskRepository {
    private static final String INSERT_SQL = """
            INSERT INTO report_task (id, tenant_id, document_object_key, status, version, created_at, updated_at)
            VALUES (:id, :tenant_id, :document_object_key, :status, :version, :created_at, :updated_at)
            """;

    private static final String SELECT_SQL = """
            SELECT id, tenant_id, document_object_key, status, version, created_at, updated_at
            FROM report_task
            WHERE tenant_id = :tenant_id AND id = :id
            """;

    private static final String UPDATE_SQL = """
            UPDATE report_task
            SET status = :status, version = :new_version, updated_at = :updated_at
            WHERE tenant_id = :tenant_id AND id = :id AND version = :expected_version
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresReportTaskRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReportTask save(ReportTask task) {
        jdbcTemplate.update(INSERT_SQL, taskParameters(task));
        return task;
    }

    @Override
    public boolean update(ReportTask task, long expectedVersion) {
        MapSqlParameterSource parameters = taskParameters(task)
                .addValue("new_version", task.version())
                .addValue("expected_version", expectedVersion);
        return jdbcTemplate.update(UPDATE_SQL, parameters) == 1;
    }

    @Override
    public Optional<ReportTask> findById(TenantId tenantId, String taskId) {
        try {
            ReportTask task = jdbcTemplate.queryForObject(SELECT_SQL,
                    new MapSqlParameterSource().addValue("tenant_id", tenantId.value()).addValue("id", taskId),
                    PostgresReportTaskRepository::mapTask);
            return Optional.ofNullable(task);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private static MapSqlParameterSource taskParameters(ReportTask task) {
        return new MapSqlParameterSource()
                .addValue("id", task.id())
                .addValue("tenant_id", task.tenantId().value())
                .addValue("document_object_key", task.documentObjectKey())
                .addValue("status", task.status().name())
                .addValue("version", task.version())
                .addValue("created_at", task.createdAt())
                .addValue("updated_at", task.updatedAt());
    }

    private static ReportTask mapTask(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReportTask(
                resultSet.getString("id"),
                new TenantId(resultSet.getString("tenant_id")),
                resultSet.getString("document_object_key"),
                ReportTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at"));
    }

    private static Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}
