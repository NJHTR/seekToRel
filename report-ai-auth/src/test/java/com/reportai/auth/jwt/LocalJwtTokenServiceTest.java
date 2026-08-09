package com.reportai.auth.jwt;

import com.reportai.auth.model.Permission;
import com.reportai.auth.model.UserAccount;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LocalJwtTokenServiceTest {
    @Test
    void issuesVerifiableTenantBoundAccessTokenAndHashedRefreshMaterial() {
        LocalAuthProperties properties = new LocalAuthProperties();
        properties.setEnabled(true);
        properties.setSigningSecret("0123456789012345678901234567890123456789012345678901234567890123");
        LocalJwtTokenService service = new LocalJwtTokenService(properties);
        Instant now = Instant.now();
        UserAccount account = new UserAccount("user-1", TenantId.of("tenant-1"), "user@example.com",
                "{bcrypt}hash", "User", com.reportai.auth.model.AccountStatus.ACTIVE, now, now, null);

        IssuedTokenPair pair = service.issue(account, Set.of(Permission.DOCUMENT_WRITE, Permission.TASK_CREATE));

        var principal = service.verify(pair.accessToken());
        assertEquals("user-1", principal.subjectId());
        assertEquals(TenantId.of("tenant-1"), principal.tenantId());
        assertEquals(Set.of(Permission.DOCUMENT_WRITE, Permission.TASK_CREATE), principal.permissions());
        assertFalse(pair.refreshToken().rawToken().isBlank());
        assertNotEquals(pair.refreshToken().rawToken(), pair.refreshToken().tokenHash());
        assertEquals(pair.refreshToken().tokenHash(), LocalJwtTokenService.hashRefreshToken(pair.refreshToken().rawToken()));
    }
}
