package com.reportai.storage.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.agent.Conversation;
import com.reportai.agent.ConversationMessage;
import com.reportai.agent.ConversationMessageCursor;
import com.reportai.agent.ConversationRepository;
import com.reportai.agent.ConversationRole;
import com.reportai.common.tenant.TenantId;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL conversation adapter with tenant-bound lookups and optimistic-lock updates. */
public final class PostgresConversationRepository implements ConversationRepository {
    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgresConversationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Conversation save(Conversation conversation) {
        jdbc.update("""
                INSERT INTO conversation (id, tenant_id, task_id, title, version, created_at, updated_at)
                VALUES (:id, :tenant_id, :task_id, :title, :version, :created_at, :updated_at)
                """, conversationParameters(conversation));
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(TenantId tenantId, String conversationId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id, tenant_id, task_id, title, version, created_at, updated_at
                    FROM conversation
                    WHERE tenant_id = :tenant_id AND id = :id
                    """, new MapSqlParameterSource().addValue("tenant_id", tenantId.value())
                    .addValue("id", conversationId), PostgresConversationRepository::mapConversation));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean update(Conversation conversation, long expectedVersion) {
        return jdbc.update("""
                UPDATE conversation
                SET task_id = :task_id, title = :title, version = :version, updated_at = :updated_at
                WHERE tenant_id = :tenant_id AND id = :id AND version = :expected_version
                """, conversationParameters(conversation).addValue("expected_version", expectedVersion)) == 1;
    }

    @Override
    public ConversationMessage appendMessage(ConversationMessage message) {
        String metadata = writeMetadata(message.metadata());
        jdbc.update("""
                INSERT INTO conversation_message
                    (id, tenant_id, conversation_id, role, content, metadata, created_at)
                VALUES (:id, :tenant_id, :conversation_id, :role, :content,
                        CAST(:metadata AS jsonb), :created_at)
                """, new MapSqlParameterSource().addValue("id", message.id())
                .addValue("tenant_id", message.tenantId().value())
                .addValue("conversation_id", message.conversationId())
                .addValue("role", message.role().name()).addValue("content", message.content())
                .addValue("metadata", metadata).addValue("created_at", Timestamp.from(message.createdAt())));
        return message;
    }

    @Override
    public List<ConversationMessage> listMessages(TenantId tenantId, String conversationId,
                                                   java.time.Instant before, int limit) {
        return listMessagesAfter(tenantId, conversationId,
                before == null ? null : ConversationMessageCursor.atTimestamp(before), limit);
    }

    @Override
    public List<ConversationMessage> listMessagesAfter(TenantId tenantId, String conversationId,
                                                       ConversationMessageCursor before, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenant_id", tenantId.value()).addValue("conversation_id", conversationId)
                .addValue("limit", limit);
        String sql = """
                SELECT id, tenant_id, conversation_id, role, content, metadata, created_at
                FROM conversation_message
                WHERE tenant_id = :tenant_id AND conversation_id = :conversation_id
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """;
        if (before != null && before.timestampOnly()) {
            sql = """
                    SELECT id, tenant_id, conversation_id, role, content, metadata, created_at
                    FROM conversation_message
                    WHERE tenant_id = :tenant_id AND conversation_id = :conversation_id
                      AND created_at < :before_created_at
                    ORDER BY created_at DESC, id DESC
                    LIMIT :limit
                    """;
            parameters.addValue("before_created_at", Timestamp.from(before.createdAt()));
        } else if (before != null) {
            sql = """
                    SELECT id, tenant_id, conversation_id, role, content, metadata, created_at
                    FROM conversation_message
                    WHERE tenant_id = :tenant_id AND conversation_id = :conversation_id
                      AND (created_at < :before_created_at
                           OR (created_at = :before_created_at AND id < :before_id))
                    ORDER BY created_at DESC, id DESC
                    LIMIT :limit
                    """;
            parameters.addValue("before_created_at", Timestamp.from(before.createdAt()))
                    .addValue("before_id", before.messageId());
        }
        return jdbc.query(sql, parameters, this::mapMessage);
    }

    private MapSqlParameterSource conversationParameters(Conversation conversation) {
        return new MapSqlParameterSource().addValue("id", conversation.id())
                .addValue("tenant_id", conversation.tenantId().value()).addValue("task_id", conversation.taskId())
                .addValue("title", conversation.title()).addValue("version", conversation.version())
                .addValue("created_at", Timestamp.from(conversation.createdAt()))
                .addValue("updated_at", Timestamp.from(conversation.updatedAt()));
    }

    private String writeMetadata(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("conversation message metadata cannot be serialized", failure);
        }
    }

    private ConversationMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            Map<String, String> metadata = objectMapper.readValue(resultSet.getString("metadata"), METADATA_TYPE);
            return new ConversationMessage(resultSet.getString("id"), new TenantId(resultSet.getString("tenant_id")),
                    resultSet.getString("conversation_id"), ConversationRole.valueOf(resultSet.getString("role")),
                    resultSet.getString("content"), metadata, resultSet.getTimestamp("created_at").toInstant());
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new SQLException("conversation message metadata is invalid", failure);
        }
    }

    private static Conversation mapConversation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Conversation(resultSet.getString("id"), new TenantId(resultSet.getString("tenant_id")),
                resultSet.getString("task_id"), resultSet.getString("title"), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
    }
}
