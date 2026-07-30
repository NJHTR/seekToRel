package com.reportai.storage.postgres;

import com.reportai.agent.ConversationMemorySnapshot;
import com.reportai.agent.ConversationMemorySnapshotRepository;
import com.reportai.agent.ConversationMessageCursor;
import com.reportai.common.tenant.TenantId;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL adapter for tenant-scoped, CAS-protected memory summaries. */
public final class PostgresConversationMemorySnapshotRepository
        implements ConversationMemorySnapshotRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresConversationMemorySnapshotRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<ConversationMemorySnapshot> find(TenantId tenantId, String conversationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT tenant_id, conversation_id, through_created_at, through_message_id,
                           summary, version, created_at, updated_at
                    FROM conversation_memory_snapshot
                    WHERE tenant_id = :tenant_id AND conversation_id = :conversation_id
                    """, new MapSqlParameterSource().addValue("tenant_id", tenantId.value())
                    .addValue("conversation_id", conversationId),
                    PostgresConversationMemorySnapshotRepository::map));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public ConversationMemorySnapshot save(ConversationMemorySnapshot snapshot) {
        jdbc.update("""
                INSERT INTO conversation_memory_snapshot
                    (tenant_id, conversation_id, through_created_at, through_message_id,
                     summary, version, created_at, updated_at)
                VALUES (:tenant_id, :conversation_id, :through_created_at, :through_message_id,
                        :summary, :version, :created_at, :updated_at)
                """, parameters(snapshot));
        return snapshot;
    }

    @Override
    public boolean update(ConversationMemorySnapshot snapshot, long expectedVersion) {
        return jdbc.update("""
                UPDATE conversation_memory_snapshot
                SET through_created_at = :through_created_at,
                    through_message_id = :through_message_id,
                    summary = :summary,
                    version = :version,
                    updated_at = :updated_at
                WHERE tenant_id = :tenant_id AND conversation_id = :conversation_id
                  AND version = :expected_version
                """, parameters(snapshot).addValue("expected_version", expectedVersion)) == 1;
    }

    private static MapSqlParameterSource parameters(ConversationMemorySnapshot snapshot) {
        return new MapSqlParameterSource().addValue("tenant_id", snapshot.tenantId().value())
                .addValue("conversation_id", snapshot.conversationId())
                .addValue("through_created_at", snapshot.through().createdAt())
                .addValue("through_message_id", snapshot.through().messageId())
                .addValue("summary", snapshot.summary()).addValue("version", snapshot.version())
                .addValue("created_at", snapshot.createdAt()).addValue("updated_at", snapshot.updatedAt());
    }

    private static ConversationMemorySnapshot map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ConversationMemorySnapshot(new TenantId(resultSet.getString("tenant_id")),
                resultSet.getString("conversation_id"),
                new ConversationMessageCursor(resultSet.getTimestamp("through_created_at").toInstant(),
                        resultSet.getString("through_message_id")), resultSet.getString("summary"),
                resultSet.getLong("version"), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
