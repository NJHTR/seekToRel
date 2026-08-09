package com.reportai.application.auth;

import com.reportai.api.auth.AuthTokenResponse;
import com.reportai.api.auth.LoginRequest;
import com.reportai.api.auth.LogoutRequest;
import com.reportai.api.auth.RefreshTokenRequest;
import com.reportai.api.auth.RegisterRequest;
import com.reportai.auth.PasswordPolicy;
import com.reportai.auth.jwt.IssuedTokenPair;
import com.reportai.auth.jwt.LocalJwtTokenService;
import com.reportai.auth.jwt.RefreshTokenMaterial;
import com.reportai.auth.model.AccountStatus;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.RefreshTokenRecord;
import com.reportai.auth.model.UserAccount;
import com.reportai.auth.port.AccountRepository;
import com.reportai.auth.ratelimit.AuthAttemptGuard;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.tenant.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Application boundary for registration, login, token rotation and logout. */
@Service
@ConditionalOnProperty(prefix = "report-ai.auth.local", name = "enabled", havingValue = "true")
public class AuthenticationApplicationService {
    private static final String DUMMY_BCRYPT_HASH = "$2a$12$1PWH1I9QRqDJULl.EgJw/Og.z.qKFH.jDiJMx87llucAlfR4vla4a";
    private static final Set<Permission> DEFAULT_PERMISSIONS = Set.of(
            Permission.DOCUMENT_READ, Permission.DOCUMENT_WRITE, Permission.TASK_CREATE,
            Permission.TASK_READ, Permission.EXPORT_READ);

    private final AccountRepository accounts;
    private final IdGenerator ids;
    private final PasswordEncoder passwordEncoder;
    private final LocalJwtTokenService tokens;
    private final AuthAttemptGuard attemptGuard;
    private final Clock clock;

    @Autowired
    public AuthenticationApplicationService(AccountRepository accounts,
                                            IdGenerator ids,
                                            PasswordEncoder passwordEncoder,
                                            LocalJwtTokenService tokens,
                                            AuthAttemptGuard attemptGuard) {
        this(accounts, ids, passwordEncoder, tokens, attemptGuard, Clock.systemUTC());
    }

    AuthenticationApplicationService(AccountRepository accounts,
                                     IdGenerator ids,
                                     PasswordEncoder passwordEncoder,
                                     LocalJwtTokenService tokens,
                                     AuthAttemptGuard attemptGuard,
                                     Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.attemptGuard = Objects.requireNonNull(attemptGuard, "attemptGuard");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request, String userAgent) {
        Objects.requireNonNull(request, "request");
        String email = normalizeEmail(request.email());
        attemptGuard.checkAndRecord("register", email, clientAddress());
        validatePassword(request.password());
        String displayName = normalizeText(request.displayName(), "displayName", 128);
        String tenantName = normalizeText(request.tenantName(), "tenantName", 128);
        Instant now = Instant.now(clock);
        UserAccount account = new UserAccount(ids.nextId(), TenantId.of(ids.nextId()), email,
                passwordEncoder.encode(request.password()), displayName, AccountStatus.ACTIVE, now, now, null);
        try {
            accounts.createTenantAndAccount(account, tenantName);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ErrorCode.CONFLICT, "email is already registered");
        }
        return issueAndPersist(account, userAgent, now);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String userAgent) {
        Objects.requireNonNull(request, "request");
        String email = normalizeEmail(request.email());
        attemptGuard.checkAndRecord("login", email, clientAddress());
        UserAccount account = accounts.findByEmail(email).orElse(null);
        if (account == null) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            throw invalidCredentials();
        }
        if (!account.active() || !passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw invalidCredentials();
        }
        Instant now = Instant.now(clock);
        accounts.updateLastLogin(account.id(), now);
        return issueAndPersist(account, userAgent, now);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokenResponse refresh(RefreshTokenRequest request, String userAgent) {
        Objects.requireNonNull(request, "request");
        String hash = LocalJwtTokenService.hashRefreshToken(request.refreshToken());
        attemptGuard.checkAndRecord("refresh", hash, clientAddress());
        Instant now = Instant.now(clock);
        RefreshTokenRecord current = accounts.findRefreshTokenByHash(hash).orElseThrow(this::invalidCredentials);
        if (!current.usableAt(now)) {
            if (current.revokedAt() != null) {
                accounts.revokeAllRefreshTokens(current.userId(), now);
            }
            throw invalidCredentials();
        }
        if (!accounts.revokeRefreshToken(current.id(), now)) {
            accounts.revokeAllRefreshTokens(current.userId(), now);
            throw invalidCredentials();
        }
        UserAccount account = accounts.findById(current.userId()).filter(UserAccount::active)
                .orElseThrow(this::invalidCredentials);
        return issueAndPersist(account, userAgent, now);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        Objects.requireNonNull(request, "request");
        String hash = LocalJwtTokenService.hashRefreshToken(request.refreshToken());
        accounts.findRefreshTokenByHash(hash).ifPresent(token ->
                accounts.revokeRefreshToken(token.id(), Instant.now(clock)));
    }

    private AuthTokenResponse issueAndPersist(UserAccount account, String userAgent, Instant now) {
        IssuedTokenPair issued = tokens.issue(account, DEFAULT_PERMISSIONS);
        RefreshTokenMaterial refresh = issued.refreshToken();
        accounts.saveRefreshToken(new RefreshTokenRecord(refresh.id(), account.id(), account.tenantId(),
                refresh.tokenHash(), refresh.expiresAt(), null, now, userAgent));
        return new AuthTokenResponse(issued.accessToken(), tokens.accessTokenExpiresInSeconds(issued.accessTokenExpiresAt()),
                refresh.rawToken(), tokens.refreshTokenExpiresInSeconds(refresh.expiresAt()), account.id(),
                account.tenantId().value(), account.email(), account.displayName());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "invalid credentials");
    }

    private static String normalizeEmail(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "email is invalid");
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 320 || normalized.isBlank() || !normalized.contains("@")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "email is invalid");
        }
        return normalized;
    }

    private static String normalizeText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " is invalid");
        }
        return value.trim();
    }

    private static void validatePassword(String password) {
        try {
            PasswordPolicy.validate(password);
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "password does not meet the security policy");
        }
    }

    private static String clientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return "";
    }
}
