package com.reportai.storage;

import com.reportai.common.tenant.TenantId;
import com.reportai.storage.objectstore.TenantArtifactStorageResolver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class TenantArtifactStorageResolverTest {
    @Test
    void confinesAndVerifiesTaskArtifact() throws Exception {
        TenantId tenant = TenantId.of("tenant-a");
        byte[] bytes = "plot".getBytes(StandardCharsets.UTF_8);
        String prefix = prefix(tenant.value());
        String key = "tasks/" + prefix + "/task-1/artifacts/digest/plot.png";
        StoredObject reference = new StoredObject(key, "image/png", bytes.length, sha256(bytes));
        MemoryStorage storage = new MemoryStorage();
        storage.values.put(key, bytes);

        try (InputStream input = new TenantArtifactStorageResolver(storage).open(tenant, reference)) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TenantArtifactStorageResolver(storage).open(TenantId.of("tenant-b"), reference));
        assertThrows(IllegalArgumentException.class,
                () -> new TenantArtifactStorageResolver(storage).open(tenant,
                        new StoredObject("documents/" + prefix + "/wrong", "image/png", bytes.length, sha256(bytes))));
    }

    private static String prefix(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static final class MemoryStorage implements ObjectStorage {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public String put(String objectKey, InputStream data, long contentLength, String contentType) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                data.transferTo(output);
                values.put(objectKey, output.toByteArray());
                return objectKey;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(values.get(objectKey));
        }

        @Override
        public void delete(String objectKey) {
            values.remove(objectKey);
        }
    }
}
