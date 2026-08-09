package com.reportai.auth.port;

import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.common.tenant.TenantId;

/**
 * Single authorization boundary used by application services. Implementations must authorize
 * against the verified principal in {@code AuthenticationContext}, never against a tenant id
 * supplied by a client.
 */
public interface AuthorizationPort {
    TenantPrincipal requireAuthenticated();

    void requirePermission(Permission permission);

    void requireAnyPermission(Iterable<Permission> permissions);

    void requireTenant(TenantId targetTenantId);

    boolean isAllowed(Permission permission);
}
