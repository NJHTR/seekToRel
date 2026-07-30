package com.reportai.document.model;

import com.reportai.common.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Creates tenant-confined, provider-neutral object keys for document assets. */
public final class DocumentAssetKeyFactory {
    private DocumentAssetKeyFactory() {
    }

    public static String forDocument(TenantId tenantId, String documentId, int ordinal,
                                     String originalFileName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(documentId, "documentId");
        if (ordinal <= 0) {
            throw new IllegalArgumentException("asset ordinal must be positive");
        }
        String safeName = DocumentAsset.safeFileName(originalFileName);
        return "documents/" + digest(tenantId.value()) + "/" + digest(documentId)
                + "/assets/" + ordinal + "-" + safeName;
    }

    private static String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
