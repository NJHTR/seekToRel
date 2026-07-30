package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * An authenticated subject and the tenant selected by the verified credential.
 *
 * <p>The tenant is deliberately not sourced from a request parameter/header. Implementations of
 * {@code AuthenticationResolver} must derive it from a verified JWT/session/API-key claim or from
 * a server-side identity lookup before constructing this value.</p>
 */
public record TenantPrincipal(
        String subjectId,
        TenantId tenantId,
        Set<Permission> permissions,
        Instant expiresAt,
        String tokenId) {

    public TenantPrincipal {
        subjectId = requireText(subjectId, "subjectId", 1, 200);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        permissions = copyPermissions(permissions);
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        tokenId = requireText(tokenId, "tokenId", 1, 200);
    }

    public static TenantPrincipal authenticated(String subjectId,
                                                TenantId tenantId,
                                                Collection<Permission> permissions,
                                                Instant expiresAt,
                                                String tokenId) {
        return new TenantPrincipal(subjectId, tenantId,
                permissions == null ? Set.of() : Set.copyOf(permissions), expiresAt, tokenId);
    }

    public boolean hasPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        return permissions.contains(permission) || permissions.contains(Permission.PLATFORM_ADMIN);
    }

    public boolean isExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return !expiresAt.isAfter(Instant.now(clock));
    }

    private static Set<Permission> copyPermissions(Collection<Permission> permissions) {
        Objects.requireNonNull(permissions, "permissions");
        EnumSet<Permission> copy = EnumSet.noneOf(Permission.class);
        for (Permission permission : permissions) {
            copy.add(Objects.requireNonNull(permission, "permissions must not contain null"));
        }
        return Set.copyOf(copy);
    }

    private static String requireText(String value, String name, int min, int max) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(name + " length must be between " + min + " and " + max);
        }
        return normalized;
    }
}
