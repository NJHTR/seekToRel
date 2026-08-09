package com.reportai.storage.postgres;

import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.idempotency.IdempotencyRecord;
import com.reportai.common.idempotency.IdempotencyStore;
import com.reportai.common.tenant.TenantId;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public final class PostgresIdempotencyStore implements IdempotencyStore {
    private static final String FIND_SQL = """
            SELECT tenant_id, operation, idempotency_key, request_hash, resource_id, status, expires_at
            FROM idempotency_record
            WHERE tenant_id = :tenant_id AND operation = :operation
              AND idempotency_key = :idempotency_key AND expires_at > CURRENT_TIMESTAMP
            """;

    private static final String RESERVE_SQL = """
            INSERT INTO idempotency_record
                (tenant_id, operation, idempotency_key, request_hash, resource_id, status, expires_at, created_at, updated_at)
            VALUES (:tenant_id, :operation, :idempotency_key, :request_hash, :resource_id, :status, :expires_at,
                    :created_at, :updated_at)
            ON CONFLICT (tenant_id, operation, idempotency_key) DO UPDATE
            SET request_hash = EXCLUDED.request_hash,
                resource_id = EXCLUDED.resource_id,
                status = EXCLUDED.status,
                expires_at = EXCLUDED.expires_at,
                created_at = EXCLUDED.created_at,
                updated_at = EXCLUDED.updated_at
            WHERE idempotency_record.expires_at <= CURRENT_TIMESTAMP
            """;

    private static final String COMPLETE_SQL = """
            UPDATE idempotency_record
            SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = :tenant_id AND operation = :operation AND idempotency_key = :idempotency_key
            """;

    private static final String RELEASE_SQL = """
            DELETE FROM idempotency_record
            WHERE tenant_id = :tenant_id AND operation = :operation AND idempotency_key = :idempotency_key
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresIdempotencyStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdempotencyRecord> find(TenantId tenantId, String operation, IdempotencyKey key) {
        try {
            IdempotencyRecord record = jdbcTemplate.queryForObject(FIND_SQL,
                    keyParameters(tenantId, operation, key), PostgresIdempotencyStore::mapRecord);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean reserve(IdempotencyRecord record) {
        Instant now = Instant.now();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenant_id", record.tenantId().value())
                .addValue("operation", record.operation())
                .addValue("idempotency_key", record.key().value())
                .addValue("request_hash", record.requestHash())
                .addValue("resource_id", record.resourceId())
                .addValue("status", record.status().name())
                .addValue("expires_at", Timestamp.from(record.expiresAt()))
                .addValue("created_at", Timestamp.from(now))
                .addValue("updated_at", Timestamp.from(now));
        return jdbcTemplate.update(RESERVE_SQL, parameters) == 1;
    }

    @Override
    public void complete(TenantId tenantId, String operation, IdempotencyKey key) {
        jdbcTemplate.update(COMPLETE_SQL, keyParameters(tenantId, operation, key));
    }

    @Override
    public void release(TenantId tenantId, String operation, IdempotencyKey key) {
        jdbcTemplate.update(RELEASE_SQL, keyParameters(tenantId, operation, key));
    }

    private static MapSqlParameterSource keyParameters(TenantId tenantId, String operation, IdempotencyKey key) {
        return new MapSqlParameterSource()
                .addValue("tenant_id", tenantId.value())
                .addValue("operation", operation)
                .addValue("idempotency_key", key.value());
    }

    private static IdempotencyRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new IdempotencyRecord(
                new TenantId(resultSet.getString("tenant_id")),
                resultSet.getString("operation"),
                new IdempotencyKey(resultSet.getString("idempotency_key")),
                resultSet.getString("request_hash"),
                resultSet.getString("resource_id"),
                IdempotencyRecord.Status.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("expires_at").toInstant());
    }
}
