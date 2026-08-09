package com.reportai.auth.model;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAuditEntryTest {
    @Test
    void allowsSystemActorButRequiresRoleOnlyForRoleActions() {
        AccountAuditEntry verification = new AccountAuditEntry(
                "audit-1", TenantId.of("tenant-1"), null, "user-1",
                AccountAuditAction.EMAIL_VERIFIED, null, "", "request-1", "", "",
                Instant.parse("2026-08-06T00:00:00Z"));

        assertNull(verification.actorUserId());
        assertEquals(AccountAuditAction.EMAIL_VERIFIED, verification.action());
        assertThrows(IllegalArgumentException.class, () -> new AccountAuditEntry(
                "audit-2", TenantId.of("tenant-1"), "admin-1", "user-1",
                AccountAuditAction.EMAIL_VERIFIED, AccountRole.MEMBER, "", "", "", "",
                Instant.parse("2026-08-06T00:00:00Z")));
    }
}
