package com.reportai.auth.service;

import com.reportai.auth.exception.AccessDeniedException;
import com.reportai.auth.model.AccountRole;
import com.reportai.auth.model.AccountStatus;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAdministrationPolicyTest {
    private static final Instant EXPIRY = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void tenantAdminCanAdministerOnlyItsOwnTenant() {
        TenantPrincipal actor = principal("admin", "tenant-a", Permission.TENANT_ADMIN);

        assertDoesNotThrow(() -> AccountAdministrationPolicy.requireCanAdminister(actor, TenantId.of("tenant-a")));
        assertThrows(AccessDeniedException.class,
                () -> AccountAdministrationPolicy.requireCanAdminister(actor, TenantId.of("tenant-b")));
    }

    @Test
    void platformAdminCanAdministerAcrossTenants() {
        TenantPrincipal actor = principal("platform", "tenant-a", Permission.PLATFORM_ADMIN);

        assertDoesNotThrow(() -> AccountAdministrationPolicy.requireCanAdminister(actor, TenantId.of("tenant-b")));
        assertDoesNotThrow(() -> AccountAdministrationPolicy.requireCanChangeRole(
                actor, TenantId.of("tenant-b"), "user-b", AccountRole.PLATFORM_ADMIN));
    }

    @Test
    void administratorCannotLockOrDisableItself() {
        TenantPrincipal actor = principal("admin", "tenant-a", Permission.TENANT_ADMIN);

        assertThrows(AccessDeniedException.class, () -> AccountAdministrationPolicy.requireCanChangeStatus(
                actor, TenantId.of("tenant-a"), "admin", AccountStatus.LOCKED));
        assertThrows(AccessDeniedException.class, () -> AccountAdministrationPolicy.requireCanChangeStatus(
                actor, TenantId.of("tenant-a"), "admin", AccountStatus.DISABLED));
    }

    @Test
    void tenantAdminCannotGrantPlatformRole() {
        TenantPrincipal actor = principal("admin", "tenant-a", Permission.TENANT_ADMIN);

        assertThrows(AccessDeniedException.class, () -> AccountAdministrationPolicy.requireCanChangeRole(
                actor, TenantId.of("tenant-a"), "user-a", AccountRole.PLATFORM_ADMIN));
        assertDoesNotThrow(() -> AccountAdministrationPolicy.requireCanChangeRole(
                actor, TenantId.of("tenant-a"), "user-a", AccountRole.TENANT_ADMIN));
    }

    private static TenantPrincipal principal(String subject, String tenant, Permission permission) {
        return TenantPrincipal.authenticated(subject, TenantId.of(tenant), Set.of(permission), EXPIRY, "token-1");
    }
}
