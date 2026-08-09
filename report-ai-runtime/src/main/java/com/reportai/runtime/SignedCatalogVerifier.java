package com.reportai.runtime;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Verifies an administrator-signed catalog payload without contacting a network. */
public final class SignedCatalogVerifier {
    public static final int MAX_PAYLOAD_BYTES = 5 * 1024 * 1024;

    public void verify(String payload, String signatureBase64, String publicKeyBase64) {
        if (payload == null || payload.isBlank() || payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES
                || signatureBase64 == null || publicKeyBase64 == null) {
            throw invalid("catalog signature input is invalid");
        }
        try {
            byte[] signature = decode(signatureBase64, 256);
            byte[] encodedKey = decode(publicKeyBase64, 512);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encodedKey));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signature)) {
                throw invalid("catalog signature does not match payload");
            }
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            if (failure instanceof EnvironmentCatalogException catalog) throw catalog;
            throw invalid("catalog signature verification failed");
        }
    }

    private static byte[] decode(String value, int maxBytes) {
        byte[] decoded = Base64.getDecoder().decode(value);
        if (decoded.length == 0 || decoded.length > maxBytes) {
            throw new IllegalArgumentException("encoded catalog value is outside the permitted size");
        }
        return decoded;
    }

    private static EnvironmentCatalogException invalid(String message) {
        return new EnvironmentCatalogException(EnvironmentCatalogException.Code.CATALOG_SIGNATURE_INVALID, message);
    }
}
