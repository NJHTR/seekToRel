package com.reportai.auth.port;

import com.reportai.auth.model.AccountAuditEntry;
import com.reportai.auth.model.AccountRole;
import com.reportai.auth.model.AccountRoleAssignment;
import com.reportai.auth.model.AccountStatus;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;

/** Persistence boundary for auditable account administration. */
public interface AccountAdministrationRepository {
    /** Performs a tenant-bound optimistic status transition and returns whether one row changed. */
    boolean transitionStatus(TenantId tenantId,
                             String targetUserId,
                             AccountStatus expectedStatus,
                             AccountStatus newStatus,
                             long expectedVersion,
                             Instant changedAt,
                             String changedBy,
                             String reason);

    /** Inserts a role grant; implementations must enforce one active grant per user/tenant/role. */
    boolean grantRole(AccountRoleAssignment assignment);

    /** Revokes an active role grant without deleting its history. */
    boolean revokeRole(TenantId tenantId,
                       String targetUserId,
                       AccountRole role,
                       Instant revokedAt);

    /** Appends an immutable security audit event. */
    void appendAudit(AccountAuditEntry entry);
}
