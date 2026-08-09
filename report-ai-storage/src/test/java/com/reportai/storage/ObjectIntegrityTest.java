package com.reportai.storage;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectIntegrityTest {
    @Test
    void acceptsLowercaseUuidLikeOwnerIds() {
        ObjectIntegrity integrity = new ObjectIntegrity("integrity-1", TenantId.of("tenant-a"),
                "DOCUMENT", "550e8400-e29b-41d4-a716-446655440000", "SOURCE",
                new StoredObject("documents/tenant-a/source.docx", "application/octet-stream", 1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), Instant.now());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", integrity.ownerId());
    }
}
