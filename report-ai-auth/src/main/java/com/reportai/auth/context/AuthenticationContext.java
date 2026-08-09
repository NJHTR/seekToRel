package com.reportai.auth.context;

import com.reportai.auth.exception.AuthenticationRequiredException;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.common.tenant.TenantContext;

import java.time.Clock;
import java.util.Optional;
import java.util.Objects;

/**
 * Request-scoped authentication context.
 *
 * <p>This is intentionally backed by a plain {@link ThreadLocal}. The web adapter always clears
 * it in a {@code finally} block; callers must not copy it into executor threads without an
 * explicit, audited context propagation mechanism.</p>
 */
public final class AuthenticationContext {
    private static final ThreadLocal<TenantPrincipal> CURRENT = new ThreadLocal<>();
    private static final Clock CLOCK = Clock.systemUTC();

    private AuthenticationContext() {
    }

    /** Install only a principal produced by a trusted authentication resolver. */
    public static void set(TenantPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        if (principal.isExpired(CLOCK)) {
            throw new AuthenticationRequiredException("credential has expired");
        }
        CURRENT.set(principal);
        // Keep legacy/common persistence code tenant-aware without accepting a client tenant id.
        TenantContext.set(principal.tenantId());
    }

    public static Optional<TenantPrincipal> current() {
        TenantPrincipal principal = CURRENT.get();
        if (principal == null || principal.isExpired(CLOCK)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static TenantPrincipal require() {
        return current().orElseThrow(() -> new AuthenticationRequiredException("authentication is required"));
    }

    public static void clear() {
        CURRENT.remove();
        TenantContext.clear();
    }
}
