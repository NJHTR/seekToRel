package com.reportai.storage;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredObjectTest {
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void rejectsUnsafeReference() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoredObject("documents/tenant/../secret", "image/png", 1, SHA256));
        assertThrows(IllegalArgumentException.class,
                () -> new StoredObject("documents/tenant/image.png", "not-a-media-type", 1, SHA256));
    }

    @Test
    void verifiesChecksumBeforeReturningReference() throws Exception {
        byte[] bytes = "asset".getBytes(StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        StoredObject reference = new StoredObject("documents/tenant/asset.bin", "application/octet-stream",
                bytes.length, checksum);
        MemoryStorage storage = new MemoryStorage();

        assertEquals(reference, storage.put(reference, new ByteArrayInputStream(bytes)));
        assertEquals("asset", storage.value());
        assertThrows(IllegalStateException.class,
                () -> storage.put(reference, new ByteArrayInputStream("wrong".getBytes(StandardCharsets.UTF_8))));
        assertEquals("", storage.value());
    }

    private static final class MemoryStorage implements ObjectStorage {
        private ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public String put(String objectKey, InputStream data, long contentLength, String contentType) {
            try {
                output = new ByteArrayOutputStream();
                data.transferTo(output);
                if (output.size() != contentLength) {
                    throw new IllegalArgumentException("length mismatch");
                }
                return objectKey;
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(output.toByteArray());
        }

        @Override
        public void delete(String objectKey) {
            output.reset();
        }

        private String value() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
