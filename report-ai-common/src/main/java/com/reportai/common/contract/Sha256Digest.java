package com.reportai.common.contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Lower-case, content-addressed SHA-256 digest. */
public record Sha256Digest(String value) {
    public Sha256Digest {
        Objects.requireNonNull(value, "sha256");
        value = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 hexadecimal characters");
        }
    }

    public static Sha256Digest ofBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return new Sha256Digest(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static Sha256Digest ofText(String text) {
        return ofBytes(Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8));
    }
}
