package com.reportai.storage.postgres;

import com.reportai.llm.prompt.PromptRepository;
import com.reportai.llm.prompt.PromptTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL prompt catalog with immutable version identity and one active
 * version per prompt. The class is intentionally non-final because the
 * transactional save method is proxied by Spring AOP.
 */
public class PostgresPromptRepository implements PromptRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresPromptRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<PromptTemplate> find(String id, int version) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT prompt_id, version, content, active
                    FROM prompt_template
                    WHERE prompt_id = :prompt_id AND version = :version
                    """, new MapSqlParameterSource().addValue("prompt_id", id)
                    .addValue("version", version), PostgresPromptRepository::map));
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PromptTemplate> findActive(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT prompt_id, version, content, active
                    FROM prompt_template
                    WHERE prompt_id = :prompt_id AND active = TRUE
                    ORDER BY version DESC
                    LIMIT 1
                    """, new MapSqlParameterSource().addValue("prompt_id", id),
                    PostgresPromptRepository::map));
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    /** Deactivating and upserting are one application transaction to protect active-version invariants. */
    @Override
    @Transactional
    public void save(PromptTemplate template) {
        Objects.requireNonNull(template, "template");
        Instant now = Instant.now();
        if (template.active()) {
            jdbc.update("""
                    UPDATE prompt_template
                    SET active = FALSE, updated_at = :updated_at
                    WHERE prompt_id = :prompt_id AND version <> :version AND active = TRUE
                    """, new MapSqlParameterSource().addValue("prompt_id", template.id())
                    .addValue("version", template.version()).addValue("updated_at", Timestamp.from(now)));
        }
        jdbc.update("""
                INSERT INTO prompt_template (prompt_id, version, content, active, created_at, updated_at)
                VALUES (:prompt_id, :version, :content, :active, :created_at, :updated_at)
                ON CONFLICT (prompt_id, version) DO UPDATE SET
                    content = EXCLUDED.content, active = EXCLUDED.active,
                    updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource().addValue("prompt_id", template.id())
                .addValue("version", template.version()).addValue("content", template.content())
                .addValue("active", template.active()).addValue("created_at", Timestamp.from(now))
                .addValue("updated_at", Timestamp.from(now)));
    }

    private static PromptTemplate map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PromptTemplate(resultSet.getString("prompt_id"), resultSet.getInt("version"),
                resultSet.getString("content"), resultSet.getBoolean("active"));
    }
}
