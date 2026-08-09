package com.reportai.storage.postgres;

import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.FailureTransition;
import com.reportai.scheduler.QueuedTask;
import com.reportai.scheduler.RetryPolicy;
import com.reportai.scheduler.ScheduledTask;
import com.reportai.scheduler.SchedulerTaskStatus;
import com.reportai.scheduler.SchedulerTaskStore;
import com.reportai.scheduler.SchedulerIdempotencyConflictException;
import com.reportai.scheduler.SubmissionResult;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** PostgreSQL adapter using unique idempotency keys and conditional versioned updates. */
public final class PostgresSchedulerTaskStore implements SchedulerTaskStore {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresSchedulerTaskStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public SubmissionResult enqueue(QueuedTask task) {
        MapSqlParameterSource p = params(task);
        int inserted = jdbc.update("""
                INSERT INTO scheduler_task (id, tenant_id, task_type, payload, idempotency_key, request_hash,
                    max_attempts, initial_backoff_ms, max_backoff_ms, attempt, status, next_run_at, version,
                    created_at, updated_at)
                VALUES (:id, :tenant_id, :task_type, :payload, :idempotency_key, :request_hash,
                    :max_attempts, :initial_backoff_ms, :max_backoff_ms, 0, 'QUEUED', :next_run_at, 0,
                    :created_at, :updated_at)
                ON CONFLICT (tenant_id, task_type, idempotency_key) DO NOTHING
                """, p);
        ScheduledTask existing = findById(task.tenantId().value(), task.id()).orElse(null);
        if (inserted == 1) {
            return new SubmissionResult(existing, true);
        }
        ScheduledTask byKey = jdbc.queryForObject("""
                SELECT * FROM scheduler_task WHERE tenant_id=:tenant_id AND task_type=:task_type
                    AND idempotency_key=:idempotency_key
                """, new MapSqlParameterSource().addValue("tenant_id", task.tenantId().value())
                .addValue("task_type", task.type()).addValue("idempotency_key", task.idempotencyKey()),
                PostgresSchedulerTaskStore::map);
        String existingHash = jdbc.queryForObject("SELECT request_hash FROM scheduler_task WHERE id=:id",
                new MapSqlParameterSource().addValue("id", byKey.id()), String.class);
        if (!task.requestHash().equals(existingHash)) {
            throw new SchedulerIdempotencyConflictException();
        }
        return new SubmissionResult(byKey, false);
    }

    @Override
    public Optional<ScheduledTask> findById(String tenantId, String taskId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM scheduler_task WHERE tenant_id=:tenant_id AND id=:id",
                    new MapSqlParameterSource().addValue("tenant_id", tenantId).addValue("id", taskId),
                    PostgresSchedulerTaskStore::map));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ScheduledTask> claim(String taskId, String workerId, Instant now, Duration leaseDuration) {
        return claimInternal("id=:id", new MapSqlParameterSource().addValue("id", taskId), workerId, now, leaseDuration);
    }

    @Override
    public Optional<ScheduledTask> claimNext(Instant now, String workerId, Duration leaseDuration) {
        return claimInternal("id = (SELECT id FROM scheduler_task WHERE status IN ('QUEUED','RETRY_WAITING') "
                + "AND next_run_at <= :now ORDER BY next_run_at, created_at FOR UPDATE SKIP LOCKED LIMIT 1)",
                new MapSqlParameterSource(), workerId, now, leaseDuration);
    }

    private Optional<ScheduledTask> claimInternal(String predicate, MapSqlParameterSource p,
                                                   String workerId, Instant now, Duration leaseDuration) {
        p.addValue("now", Timestamp.from(now)).addValue("worker_id", workerId)
                .addValue("lease_until", Timestamp.from(now.plus(leaseDuration)));
        try {
            String sql = "UPDATE scheduler_task SET status='RUNNING', lease_owner=:worker_id, lease_until=:lease_until, "
                    + "attempt=attempt+1, version=version+1, updated_at=:now WHERE " + predicate
                    + " AND status IN ('QUEUED','RETRY_WAITING') AND next_run_at <= :now RETURNING *";
            return Optional.ofNullable(jdbc.queryForObject(sql, p, PostgresSchedulerTaskStore::map));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean renewLease(String taskId, String workerId, long version, Instant now, Duration leaseDuration) {
        return jdbc.update("UPDATE scheduler_task SET lease_until=:lease_until, version=version+1, updated_at=:now "
                        + "WHERE id=:id AND status='RUNNING' AND lease_owner=:worker_id AND version=:version",
                new MapSqlParameterSource().addValue("id", taskId).addValue("worker_id", workerId)
                        .addValue("version", version).addValue("lease_until", Timestamp.from(now.plus(leaseDuration)))
                        .addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public boolean releaseLease(String taskId, String workerId, long version, Instant now) {
        return jdbc.update("UPDATE scheduler_task SET status='QUEUED', lease_owner=NULL, lease_until=NULL, "
                        + "version=version+1, updated_at=:now WHERE id=:id AND status='RUNNING' "
                        + "AND lease_owner=:worker_id AND version=:version",
                baseVersion(taskId, workerId, version, now)) == 1;
    }

    @Override
    public boolean markSucceeded(String taskId, String workerId, long version, Instant now) {
        return jdbc.update("UPDATE scheduler_task SET status='SUCCEEDED', lease_owner=NULL, lease_until=NULL, "
                        + "version=version+1, updated_at=:now WHERE id=:id AND status='RUNNING' "
                        + "AND lease_owner=:worker_id AND version=:version", baseVersion(taskId, workerId, version, now)) == 1;
    }

    @Override
    public FailureTransition markFailure(String taskId, String workerId, long version, Instant now,
                                         boolean retryable, String errorMessage) {
        Optional<ScheduledTask> current = findByIdAny(taskId);
        if (current.isEmpty() || current.get().version() != version || !workerId.equals(current.get().leaseOwner())) {
            return new FailureTransition(SchedulerTaskStatus.FAILED, false, version);
        }
        ScheduledTask task = current.get();
        boolean retry = retryable && task.attempt() < task.retryPolicy().maxAttempts();
        SchedulerTaskStatus status = retry ? SchedulerTaskStatus.RETRY_WAITING : SchedulerTaskStatus.FAILED;
        Instant nextRun = retry ? now.plus(task.retryPolicy().backoffForAttempt(task.attempt())) : now;
        int updated = jdbc.update("UPDATE scheduler_task SET status=:status, next_run_at=:next_run_at, "
                        + "last_error=:last_error, lease_owner=NULL, lease_until=NULL, version=version+1, updated_at=:now "
                        + "WHERE id=:id AND status='RUNNING' AND lease_owner=:worker_id AND version=:version",
                new MapSqlParameterSource().addValue("id", taskId).addValue("status", status.name())
                        .addValue("next_run_at", Timestamp.from(nextRun)).addValue("now", Timestamp.from(now))
                        .addValue("last_error", errorMessage == null ? "" : errorMessage.substring(0, Math.min(4096, errorMessage.length())))
                        .addValue("worker_id", workerId).addValue("version", version));
        return new FailureTransition(status, retry, updated == 1 ? version + 1 : version);
    }

    @Override
    public boolean cancel(String taskId, String tenantId, Instant now) {
        return jdbc.update("UPDATE scheduler_task SET status='CANCELLED', lease_owner=NULL, lease_until=NULL, "
                        + "version=version+1, updated_at=:now WHERE id=:id AND tenant_id=:tenant_id "
                        + "AND status IN ('QUEUED','RETRY_WAITING','RUNNING')",
                new MapSqlParameterSource().addValue("id", taskId).addValue("tenant_id", tenantId)
                        .addValue("now", Timestamp.from(now))) == 1;
    }

    @Override
    public int releaseExpiredLeases(Instant now) {
        return jdbc.update("UPDATE scheduler_task SET status='QUEUED', lease_owner=NULL, lease_until=NULL, "
                        + "version=version+1, updated_at=:now WHERE status='RUNNING' AND lease_until < :now",
                new MapSqlParameterSource().addValue("now", Timestamp.from(now)));
    }

    private Optional<ScheduledTask> findByIdAny(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM scheduler_task WHERE id=:id",
                    new MapSqlParameterSource().addValue("id", id), PostgresSchedulerTaskStore::map));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private static MapSqlParameterSource params(QueuedTask task) {
        return new MapSqlParameterSource().addValue("id", task.id()).addValue("tenant_id", task.tenantId().value())
                .addValue("task_type", task.type()).addValue("payload", task.payload())
                .addValue("idempotency_key", task.idempotencyKey()).addValue("request_hash", task.requestHash())
                .addValue("max_attempts", task.retryPolicy().maxAttempts())
                .addValue("initial_backoff_ms", task.retryPolicy().initialBackoff().toMillis())
                .addValue("max_backoff_ms", task.retryPolicy().maxBackoff().toMillis())
                .addValue("next_run_at", Timestamp.from(task.createdAt())).addValue("created_at", Timestamp.from(task.createdAt()))
                .addValue("updated_at", Timestamp.from(task.createdAt()));
    }

    private static MapSqlParameterSource baseVersion(String id, String worker, long version, Instant now) {
        return new MapSqlParameterSource().addValue("id", id).addValue("worker_id", worker)
                .addValue("version", version).addValue("now", Timestamp.from(now));
    }

    private static ScheduledTask map(ResultSet rs, int row) throws SQLException {
        return new ScheduledTask(rs.getString("id"), new TenantId(rs.getString("tenant_id")),
                rs.getString("task_type"), rs.getString("payload"), rs.getString("idempotency_key"),
                new RetryPolicy(rs.getInt("max_attempts"), Duration.ofMillis(rs.getLong("initial_backoff_ms")),
                        Duration.ofMillis(rs.getLong("max_backoff_ms"))), rs.getInt("attempt"),
                SchedulerTaskStatus.valueOf(rs.getString("status")), rs.getTimestamp("next_run_at").toInstant(),
                rs.getString("lease_owner"), rs.getTimestamp("lease_until") == null ? null : rs.getTimestamp("lease_until").toInstant(),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

}
