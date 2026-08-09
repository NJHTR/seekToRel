package com.reportai.storage.postgres;

import com.reportai.auth.model.AccountStatus;
import com.reportai.auth.model.RefreshTokenRecord;
import com.reportai.auth.model.UserAccount;
import com.reportai.auth.port.AccountRepository;
import com.reportai.common.tenant.TenantId;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** PostgreSQL persistence adapter for local accounts and refresh-token rotation. */
public final class PostgresAccountRepository implements AccountRepository {
    private static final String ACCOUNT_COLUMNS = "id, tenant_id, email, password_hash, display_name, status, "
            + "created_at, updated_at, last_login_at";
    private static final String REFRESH_COLUMNS = "id, user_id, tenant_id, token_hash, expires_at, revoked_at, "
            + "created_at, user_agent";
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createTenantAndAccount(UserAccount account, String tenantName) {
        Instant now = account.createdAt();
        jdbc.update("""
                INSERT INTO tenant (id, name, status, created_at, updated_at)
                VALUES (:tenant_id, :tenant_name, 'ACTIVE', :created_at, :updated_at)
                """, new MapSqlParameterSource()
                .addValue("tenant_id", account.tenantId().value())
                .addValue("tenant_name", tenantName)
                .addValue("created_at", timestamp(now))
                .addValue("updated_at", timestamp(now)));
        jdbc.update("""
                INSERT INTO user_account
                    (id, tenant_id, email, password_hash, display_name, status, created_at, updated_at)
                VALUES (:id, :tenant_id, :email, :password_hash, :display_name, :status,
                        :created_at, :updated_at)
                """, accountParameters(account));
    }

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT " + ACCOUNT_COLUMNS
                    + " FROM user_account WHERE lower(email) = :email",
                    new MapSqlParameterSource("email", normalizedEmail), this::mapAccount));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT " + ACCOUNT_COLUMNS
                    + " FROM user_account WHERE id = :id",
                    new MapSqlParameterSource("id", userId), this::mapAccount));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void updateLastLogin(String userId, Instant lastLoginAt) {
        jdbc.update("UPDATE user_account SET last_login_at = :last_login_at, updated_at = :updated_at WHERE id = :id",
                new MapSqlParameterSource().addValue("id", userId)
                        .addValue("last_login_at", timestamp(lastLoginAt)).addValue("updated_at", timestamp(lastLoginAt)));
    }

    @Override
    public void saveRefreshToken(RefreshTokenRecord refreshToken) {
        jdbc.update("""
                INSERT INTO refresh_token
                    (id, user_id, tenant_id, token_hash, expires_at, revoked_at, created_at, user_agent)
                VALUES (:id, :user_id, :tenant_id, :token_hash, :expires_at, :revoked_at, :created_at, :user_agent)
                """, refreshParameters(refreshToken));
    }

    @Override
    public Optional<RefreshTokenRecord> findRefreshTokenByHash(String tokenHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT " + REFRESH_COLUMNS
                    + " FROM refresh_token WHERE token_hash = :token_hash",
                    new MapSqlParameterSource("token_hash", tokenHash), this::mapRefreshToken));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean revokeRefreshToken(String tokenId, Instant revokedAt) {
        return jdbc.update("UPDATE refresh_token SET revoked_at = :revoked_at "
                        + "WHERE id = :id AND revoked_at IS NULL AND expires_at > :revoked_at",
                new MapSqlParameterSource().addValue("id", tokenId)
                        .addValue("revoked_at", timestamp(revokedAt))) == 1;
    }

    @Override
    public int revokeAllRefreshTokens(String userId, Instant revokedAt) {
        return jdbc.update("UPDATE refresh_token SET revoked_at = :revoked_at "
                        + "WHERE user_id = :user_id AND revoked_at IS NULL",
                new MapSqlParameterSource().addValue("user_id", userId)
                        .addValue("revoked_at", timestamp(revokedAt)));
    }

    private static MapSqlParameterSource accountParameters(UserAccount account) {
        return new MapSqlParameterSource().addValue("id", account.id())
                .addValue("tenant_id", account.tenantId().value()).addValue("email", account.email())
                .addValue("password_hash", account.passwordHash()).addValue("display_name", account.displayName())
                .addValue("status", account.status().name()).addValue("created_at", timestamp(account.createdAt()))
                .addValue("updated_at", timestamp(account.updatedAt()));
    }

    private static MapSqlParameterSource refreshParameters(RefreshTokenRecord token) {
        return new MapSqlParameterSource().addValue("id", token.id()).addValue("user_id", token.userId())
                .addValue("tenant_id", token.tenantId().value()).addValue("token_hash", token.tokenHash())
                .addValue("expires_at", timestamp(token.expiresAt())).addValue("revoked_at", timestamp(token.revokedAt()))
                .addValue("created_at", timestamp(token.createdAt())).addValue("user_agent", token.userAgent());
    }

    private UserAccount mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserAccount(resultSet.getString("id"), TenantId.of(resultSet.getString("tenant_id")),
                resultSet.getString("email"), resultSet.getString("password_hash"),
                resultSet.getString("display_name"), AccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant(),
                optionalInstant(resultSet, "last_login_at"));
    }

    private RefreshTokenRecord mapRefreshToken(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RefreshTokenRecord(resultSet.getString("id"), resultSet.getString("user_id"),
                TenantId.of(resultSet.getString("tenant_id")), resultSet.getString("token_hash"),
                resultSet.getTimestamp("expires_at").toInstant(), optionalInstant(resultSet, "revoked_at"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getString("user_agent"));
    }

    private static Instant optionalInstant(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
