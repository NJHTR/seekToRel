package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignedCatalogVerifierTest {
    @Test
    void verifiesEd25519CatalogPayloadAndRejectsTampering() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        String payload = "{\"profiles\":[{\"language\":\"python\"}]}";
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        SignedCatalogVerifier verifier = new SignedCatalogVerifier();

        verifier.verify(payload, signature, publicKey);
        EnvironmentCatalogException failure = assertThrows(EnvironmentCatalogException.class,
                () -> verifier.verify(payload + " ", signature, publicKey));
        assertEquals(EnvironmentCatalogException.Code.CATALOG_SIGNATURE_INVALID, failure.code());
    }
}
