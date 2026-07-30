package com.reportai.storage.postgres;

import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.StoredObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

public final class PostgresObjectIntegrityRepository
        implements ObjectIntegrityRepository, com.reportai.storage.ObjectIntegrityReconciliationRepository {
    private static final String INSERT_SQL = """
            INSERT INTO object_integrity
                (id, tenant_id, owner_type, owner_id, role, object_key, media_type, object_size, sha256, created_at)
            VALUES (:id, :tenant_id, :owner_type, :owner_id, :role, :object_key, :media_type, :object_size, :sha256, :created_at)
            ON CONFLICT (tenant_id, owner_type, owner_id, role) DO UPDATE SET
                object_key = EXCLUDED.object_key, media_type = EXCLUDED.media_type,
                object_size = EXCLUDED.object_size, sha256 = EXCLUDED.sha256,
                verification_status = 'UNVERIFIED', verified_at = NULL,
                verification_error = NULL
            """;
    private static final String SELECT_SQL = """
            SELECT id, tenant_id, owner_type, owner_id, role, object_key, media_type, object_size, sha256, created_at
            FROM object_integrity
            WHERE tenant_id = :tenant_id AND owner_type = :owner_type AND owner_id = :owner_id
            ORDER BY role, id
            """;
    private static final String DELETE_SQL = """
            DELETE FROM object_integrity
            WHERE tenant_id = :tenant_id AND owner_type = :owner_type AND owner_id = :owner_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresObjectIntegrityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ObjectIntegrity save(ObjectIntegrity integrity) {
        jdbcTemplate.update(INSERT_SQL, new MapSqlParameterSource()
                .addValue("id", integrity.id())
                .addValue("tenant_id", integrity.tenantId().value())
                .addValue("owner_type", integrity.ownerType())
                .addValue("owner_id", integrity.ownerId())
                .addValue("role", integrity.role())
                .addValue("object_key", integrity.object().objectKey())
                .addValue("media_type", integrity.object().mediaType())
                .addValue("object_size", integrity.object().size())
                .addValue("sha256", integrity.object().sha256())
                .addValue("created_at", integrity.createdAt()));
        return integrity;
    }

    @Override
    public List<ObjectIntegrity> findByOwner(TenantId tenantId, String ownerType, String ownerId) {
        return jdbcTemplate.query(SELECT_SQL, new MapSqlParameterSource()
                .addValue("tenant_id", tenantId.value())
                .addValue("owner_type", ownerType)
                .addValue("owner_id", ownerId), PostgresObjectIntegrityRepository::map);
    }

    @Override
    public void deleteByOwner(TenantId tenantId, String ownerType, String ownerId) {
        jdbcTemplate.update(DELETE_SQL, new MapSqlParameterSource()
                .addValue("tenant_id", tenantId.value())
                .addValue("owner_type", ownerType)
                .addValue("owner_id", ownerId));
    }

    @Override
    public List<ObjectIntegrity> findForVerification(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return jdbcTemplate.query("""
                SELECT id, tenant_id, owner_type, owner_id, role, object_key, media_type,
                       object_size, sha256, created_at
                FROM object_integrity
                ORDER BY CASE WHEN verification_status = 'VERIFIED' THEN 1 ELSE 0 END,
                         verified_at NULLS FIRST, created_at, id
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("limit", limit),
                PostgresObjectIntegrityRepository::map);
    }

    @Override
    public List<ObjectIntegrity> findOrphanedMetadata(int limit, Instant olderThan) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        if (olderThan == null) {
            throw new IllegalArgumentException("olderThan must not be null");
        }
        return jdbcTemplate.query("""
                SELECT id, tenant_id, owner_type, owner_id, role, object_key, media_type,
                       object_size, sha256, created_at
                FROM object_integrity integrity
                WHERE integrity.created_at <= :older_than
                  AND ((integrity.owner_type = 'DOCUMENT' AND NOT EXISTS (
                           SELECT 1 FROM document d
                           WHERE d.tenant_id = integrity.tenant_id AND d.id = integrity.owner_id
                       ))
                    OR (integrity.owner_type = 'TASK' AND NOT EXISTS (
                           SELECT 1 FROM report_task t
                           WHERE t.tenant_id = integrity.tenant_id AND t.id = integrity.owner_id
                       )))
                  AND NOT EXISTS (
                      SELECT 1
                      FROM object_integrity live
                      WHERE live.object_key = integrity.object_key
                        AND live.id <> integrity.id
                        AND ((live.owner_type = 'DOCUMENT' AND EXISTS (
                                 SELECT 1 FROM document d
                                 WHERE d.tenant_id = live.tenant_id AND d.id = live.owner_id
                             ))
                          OR (live.owner_type = 'TASK' AND EXISTS (
                                 SELECT 1 FROM report_task t
                                 WHERE t.tenant_id = live.tenant_id AND t.id = live.owner_id
                             )))
                  )
                ORDER BY integrity.created_at, integrity.id
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("older_than", Timestamp.from(olderThan), Types.TIMESTAMP)
                .addValue("limit", limit), PostgresObjectIntegrityRepository::map);
    }

    @Override
    public int deleteMetadataByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (ids.size() > 1000 || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("ids must contain between 1 and 1000 non-blank values");
        }
        return jdbcTemplate.update("""
                DELETE FROM object_integrity
                WHERE id IN (:ids)
                """, new MapSqlParameterSource().addValue("ids", ids));
    }

    @Override
    public void markVerified(String id, Instant verifiedAt) {
        jdbcTemplate.update("""
                UPDATE object_integrity
                SET verification_status = 'VERIFIED', verified_at = :verified_at,
                    verification_error = NULL
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("verified_at", verifiedAt));
    }

    @Override
    public void markVerificationFailed(String id, Instant failedAt, String error) {
        String safeError = error == null ? "" : error.substring(0, Math.min(4096, error.length()));
        jdbcTemplate.update("""
                UPDATE object_integrity
                SET verification_status = 'FAILED', verified_at = :verified_at,
                    verification_error = :verification_error
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("verified_at", failedAt).addValue("verification_error", safeError));
    }

    private static ObjectIntegrity map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ObjectIntegrity(resultSet.getString("id"), new TenantId(resultSet.getString("tenant_id")),
                resultSet.getString("owner_type"), resultSet.getString("owner_id"), resultSet.getString("role"),
                new StoredObject(resultSet.getString("object_key"), resultSet.getString("media_type"),
                        resultSet.getLong("object_size"), resultSet.getString("sha256")),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
