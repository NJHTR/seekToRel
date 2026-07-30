package com.reportai.document.model;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentAssetTest {
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void validatesMetadataAndGeneratesTenantConfinedKey() {
        String key = DocumentAssetKeyFactory.forDocument(TenantId.of("tenant-a"), "doc/with/slash", 1, "plot.png");
        DocumentAsset asset = new DocumentAsset(key, "plot.png", "image/png", 4, SHA256);

        assertEquals("image/png", asset.mediaType());
        assertEquals(4, asset.size());
        assertEquals("documents/", asset.objectKey().substring(0, "documents/".length()));
        assertEquals(key, asset.objectKey());
    }

    @Test
    void rejectsTraversalAndInvalidDigest() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentAsset("documents/t/a/../secret.png", "plot.png", "image/png", 4, SHA256));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentAsset("documents/t/a/asset.png", "../plot.png", "image/png", 4, SHA256));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentAsset("documents/t/a/asset.png", "plot.png", "image/png", 4, "bad"));
    }
}
