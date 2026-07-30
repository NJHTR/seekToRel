package com.reportai.auth.port;

import com.reportai.auth.context.AuthenticationContext;
import com.reportai.auth.exception.AccessDeniedException;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.common.tenant.TenantId;

import java.util.Iterator;
import java.util.Objects;

/** Default fail-closed authorization implementation. */
public final class DefaultAuthorizationChecker implements AuthorizationPort {
    @Override
    public TenantPrincipal requireAuthenticated() {
        return AuthenticationContext.require();
    }

    @Override
    public void requirePermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        TenantPrincipal principal = requireAuthenticated();
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException("permission denied");
        }
    }

    @Override
    public void requireAnyPermission(Iterable<Permission> permissions) {
        Objects.requireNonNull(permissions, "permissions");
        TenantPrincipal principal = requireAuthenticated();
        Iterator<Permission> iterator = permissions.iterator();
        boolean supplied = false;
        while (iterator.hasNext()) {
            Permission permission = Objects.requireNonNull(iterator.next(), "permissions must not contain null");
            supplied = true;
            if (principal.hasPermission(permission)) {
                return;
            }
        }
        if (!supplied) {
            throw new IllegalArgumentException("at least one permission is required");
        }
        throw new AccessDeniedException("permission denied");
    }

    @Override
    public void requireTenant(TenantId targetTenantId) {
        Objects.requireNonNull(targetTenantId, "targetTenantId");
        TenantPrincipal principal = requireAuthenticated();
        if (!principal.tenantId().equals(targetTenantId)
                && !principal.hasPermission(Permission.PLATFORM_ADMIN)) {
            throw new AccessDeniedException("tenant access denied");
        }
    }

    @Override
    public boolean isAllowed(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        return AuthenticationContext.current().map(principal -> principal.hasPermission(permission)).orElse(false);
    }
}
