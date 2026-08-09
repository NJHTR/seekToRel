package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.Document;
import com.reportai.storage.DocumentRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public final class PostgresDocumentRepository implements DocumentRepository {
    private static final String INSERT_SQL = """
            INSERT INTO document
                (id, tenant_id, source_object_key, media_type, status, content, metadata, version, created_at, updated_at)
            VALUES (:id, :tenant_id, :source_object_key, :media_type, 'PARSED', CAST(:content AS jsonb), '{}'::jsonb,
                    0, :created_at, :updated_at)
            ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content,
                media_type = EXCLUDED.media_type, updated_at = EXCLUDED.updated_at, version = document.version + 1
            """;

    private static final String SELECT_SQL = """
            SELECT id, tenant_id, source_object_key, media_type, content, created_at, updated_at
            FROM document
            WHERE tenant_id = :tenant_id AND id = :id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Document save(Document document) {
        try {
            String content = objectMapper.writeValueAsString(document);
            jdbcTemplate.update(INSERT_SQL, new MapSqlParameterSource()
                    .addValue("id", document.id())
                    .addValue("tenant_id", document.tenantId().value())
                    .addValue("source_object_key", document.sourceObjectKey())
                    .addValue("media_type", document.mediaType())
                    .addValue("content", content)
                    .addValue("created_at", Timestamp.from(Instant.now()))
                    .addValue("updated_at", Timestamp.from(Instant.now())));
            return document;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("document serialization failed", exception);
        }
    }

    @Override
    public Optional<Document> findById(TenantId tenantId, String documentId) {
        try {
            Document document = jdbcTemplate.queryForObject(SELECT_SQL,
                    new MapSqlParameterSource().addValue("tenant_id", tenantId.value()).addValue("id", documentId),
                    (resultSet, rowNumber) -> mapDocument(resultSet));
            return Optional.ofNullable(document);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private Document mapDocument(ResultSet resultSet) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString("content"), Document.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored document JSON is invalid", exception);
        }
    }
}
