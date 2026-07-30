package com.reportai.storage.postgres;

import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.QueuedTask;
import com.reportai.scheduler.RetryPolicy;
import com.reportai.scheduler.SchedulerOutboxEntry;
import com.reportai.scheduler.SchedulerOutboxRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** PostgreSQL transactional-outbox adapter using one atomic lease claim statement. */
public final class PostgresSchedulerOutboxRepository implements SchedulerOutboxRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresSchedulerOutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public SchedulerOutboxEntry enqueue(QueuedTask task) {
        jdbc.update("""
                INSERT INTO scheduler_outbox
                    (id, tenant_id, task_type, payload, idempotency_key, max_attempts,
                     initial_backoff_ms, max_backoff_ms, attempt, status, next_attempt_at,
                     created_at, updated_at)
                VALUES (:id, :tenant_id, :task_type, :payload, :idempotency_key, :max_attempts,
                        :initial_backoff_ms, :max_backoff_ms, 0, 'PENDING', :next_attempt_at,
                        :created_at, :updated_at)
                ON CONFLICT (tenant_id, task_type, idempotency_key) DO NOTHING
                """, params(task));
        return findById(task.id()).orElseGet(() -> findByBusinessKey(task));
    }

    @Override
    public List<SchedulerOutboxEntry> claimReady(Instant now, String owner,
                                                 Duration leaseDuration, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbc.query("""
                WITH candidates AS (
                    SELECT id
                    FROM scheduler_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= :now)
                       OR (status = 'IN_FLIGHT' AND lease_until < :now)
                    ORDER BY next_attempt_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                UPDATE scheduler_outbox outbox
                SET status = 'IN_FLIGHT', lease_owner = :owner,
                    lease_until = :lease_until, attempt = outbox.attempt + 1,
                    updated_at = :now
                FROM candidates
                WHERE outbox.id = candidates.id
                RETURNING outbox.*
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now))
                .addValue("owner", owner).addValue("lease_until", Timestamp.from(now.plus(leaseDuration)))
                .addValue("limit", limit), PostgresSchedulerOutboxRepository::map);
    }

    @Override
    public boolean markPublished(String id, String owner, Instant now) {
        return jdbc.update("""
                UPDATE scheduler_outbox
                SET status = 'PUBLISHED', lease_owner = NULL, lease_until = NULL,
                    updated_at = :now
                WHERE id = :id AND status = 'IN_FLIGHT' AND lease_owner = :owner
                """, new MapSqlParameterSource().addValue("id", id).addValue("owner", owner)
                .addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean reschedule(String id, String owner, Instant now,
                              Instant nextAttemptAt, String error) {
        String safeError = error == null ? "" : error.substring(0, Math.min(4096, error.length()));
        return jdbc.update("""
                UPDATE scheduler_outbox
                SET status = 'PENDING', lease_owner = NULL, lease_until = NULL,
                    next_attempt_at = :next_attempt_at, last_error = :last_error,
                    updated_at = :now
                WHERE id = :id AND status = 'IN_FLIGHT' AND lease_owner = :owner
                """, new MapSqlParameterSource().addValue("id", id).addValue("owner", owner)
                .addValue("next_attempt_at", Timestamp.from(nextAttemptAt))
                .addValue("last_error", safeError).addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public int purgePublished(Instant before, int limit) {
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }
        return jdbc.update("""
                DELETE FROM scheduler_outbox
                WHERE id IN (
                    SELECT id FROM scheduler_outbox
                    WHERE status = 'PUBLISHED' AND updated_at < :before
                    ORDER BY updated_at, id
                    LIMIT :limit
                )
                """, new MapSqlParameterSource().addValue("before", Timestamp.from(before))
                .addValue("limit", limit));
    }

    private java.util.Optional<SchedulerOutboxEntry> findById(String id) {
        try {
            return java.util.Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM scheduler_outbox WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", id), PostgresSchedulerOutboxRepository::map));
        } catch (EmptyResultDataAccessException ignored) {
            return java.util.Optional.empty();
        }
    }

    private SchedulerOutboxEntry findByBusinessKey(QueuedTask task) {
        return jdbc.queryForObject("""
                SELECT * FROM scheduler_outbox
                WHERE tenant_id = :tenant_id AND task_type = :task_type
                  AND idempotency_key = :idempotency_key
                """, new MapSqlParameterSource().addValue("tenant_id", task.tenantId().value())
                .addValue("task_type", task.type()).addValue("idempotency_key", task.idempotencyKey()),
                PostgresSchedulerOutboxRepository::map);
    }

    private static MapSqlParameterSource params(QueuedTask task) {
        Instant now = task.createdAt();
        return new MapSqlParameterSource().addValue("id", task.id())
                .addValue("tenant_id", task.tenantId().value()).addValue("task_type", task.type())
                .addValue("payload", task.payload()).addValue("idempotency_key", task.idempotencyKey())
                .addValue("max_attempts", task.retryPolicy().maxAttempts())
                .addValue("initial_backoff_ms", task.retryPolicy().initialBackoff().toMillis())
                .addValue("max_backoff_ms", task.retryPolicy().maxBackoff().toMillis())
                .addValue("next_attempt_at", Timestamp.from(now)).addValue("created_at", Timestamp.from(now))
                .addValue("updated_at", Timestamp.from(now));
    }

    private static SchedulerOutboxEntry map(ResultSet rs, int row) throws SQLException {
        TenantId tenant = new TenantId(rs.getString("tenant_id"));
        Instant created = rs.getTimestamp("created_at").toInstant();
        QueuedTask task = new QueuedTask(rs.getString("id"), tenant, rs.getString("task_type"),
                rs.getString("payload"), rs.getString("idempotency_key"),
                new RetryPolicy(rs.getInt("max_attempts"), Duration.ofMillis(rs.getLong("initial_backoff_ms")),
                        Duration.ofMillis(rs.getLong("max_backoff_ms"))), created);
        return new SchedulerOutboxEntry(rs.getString("id"), tenant, task,
                SchedulerOutboxEntry.OutboxStatus.valueOf(rs.getString("status")), rs.getInt("attempt"),
                rs.getTimestamp("next_attempt_at").toInstant(), rs.getString("lease_owner"),
                rs.getTimestamp("lease_until") == null ? null : rs.getTimestamp("lease_until").toInstant(),
                rs.getString("last_error"), created, rs.getTimestamp("updated_at").toInstant());
    }
}
