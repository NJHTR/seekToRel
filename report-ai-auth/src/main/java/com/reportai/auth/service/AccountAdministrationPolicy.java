package com.reportai.auth.service;

import com.reportai.auth.exception.AccessDeniedException;
import com.reportai.auth.model.AccountRole;
import com.reportai.auth.model.AccountStatus;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.common.tenant.TenantId;

import java.util.Objects;

/** Fail-closed authorization rules shared by future account-admin HTTP adapters. */
public final class AccountAdministrationPolicy {
    private AccountAdministrationPolicy() {
    }

    public static void requireCanAdminister(TenantPrincipal actor, TenantId targetTenant) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetTenant, "targetTenant");
        if (!actor.hasPermission(Permission.PLATFORM_ADMIN)
                && (!actor.tenantId().equals(targetTenant)
                || !actor.hasPermission(Permission.TENANT_ADMIN))) {
            throw new AccessDeniedException("account administration is not permitted");
        }
    }

    public static void requireCanChangeStatus(TenantPrincipal actor,
                                               TenantId targetTenant,
                                               String targetUserId,
                                               AccountStatus newStatus) {
        requireCanAdminister(actor, targetTenant);
        Objects.requireNonNull(newStatus, "newStatus");
        requireTargetUser(targetUserId);
        if (actor.subjectId().equals(targetUserId)
                && (newStatus == AccountStatus.LOCKED || newStatus == AccountStatus.DISABLED)) {
            throw new AccessDeniedException("an administrator cannot disable its own account");
        }
    }

    public static void requireCanChangeRole(TenantPrincipal actor,
                                            TenantId targetTenant,
                                            String targetUserId,
                                            AccountRole role) {
        requireCanAdminister(actor, targetTenant);
        Objects.requireNonNull(role, "role");
        requireTargetUser(targetUserId);
        if (role == AccountRole.PLATFORM_ADMIN && !actor.hasPermission(Permission.PLATFORM_ADMIN)) {
            throw new AccessDeniedException("platform role requires platform administration");
        }
        if (actor.subjectId().equals(targetUserId) && role == AccountRole.PLATFORM_ADMIN) {
            throw new AccessDeniedException("platform role cannot be self-assigned");
        }
    }

    private static void requireTargetUser(String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank() || targetUserId.trim().length() > 64) {
            throw new IllegalArgumentException("targetUserId is invalid");
        }
    }
}
