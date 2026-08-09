package com.reportai.storage.postgres;

import com.reportai.agent.ConversationCompactionCandidate;
import com.reportai.agent.ConversationCompactionCandidateRepository;
import com.reportai.common.tenant.TenantId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Objects;

/** PostgreSQL maintenance query for conversations whose history advanced past its snapshot. */
public final class PostgresConversationCompactionCandidateRepository
        implements ConversationCompactionCandidateRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresConversationCompactionCandidateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<ConversationCompactionCandidate> findCandidates(int limit) {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return jdbc.query("""
                SELECT c.tenant_id, c.id
                FROM conversation c
                LEFT JOIN conversation_memory_snapshot s
                  ON s.tenant_id = c.tenant_id AND s.conversation_id = c.id
                WHERE EXISTS (
                    SELECT 1
                    FROM conversation_message first_message
                    WHERE first_message.tenant_id = c.tenant_id
                      AND first_message.conversation_id = c.id
                )
                  AND (
                    s.conversation_id IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM conversation_message newer_message
                        WHERE newer_message.tenant_id = c.tenant_id
                          AND newer_message.conversation_id = c.id
                          AND (newer_message.created_at > s.through_created_at
                               OR (newer_message.created_at = s.through_created_at
                                   AND newer_message.id > s.through_message_id))
                    )
                  )
                ORDER BY c.tenant_id, c.id
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("limit", limit),
                (resultSet, rowNumber) -> new ConversationCompactionCandidate(
                        new TenantId(resultSet.getString("tenant_id")), resultSet.getString("id")));
    }
}
