package com.reportai.storage;

import com.reportai.common.tenant.TenantId;
import com.reportai.storage.objectstore.TenantObjectStorageResolver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantObjectStorageResolverTest {
    @Test
    void verifiesTenantAndContentOnRead() throws Exception {
        TenantId tenant = TenantId.of("tenant-a");
        byte[] content = "verified".getBytes(StandardCharsets.UTF_8);
        String key = "documents/" + prefix(tenant.value()) + "/doc-1.docx";
        StoredObject reference = new StoredObject(key,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content.length, sha256(content));
        MemoryStorage storage = new MemoryStorage();
        storage.values.put(key, content);

        try (InputStream input = new TenantObjectStorageResolver(storage).open(tenant, reference)) {
            assertArrayEquals(content, input.readAllBytes());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TenantObjectStorageResolver(storage).open(TenantId.of("tenant-b"), reference));
    }

    @Test
    void rejectsTamperedObject() throws Exception {
        TenantId tenant = TenantId.of("tenant-a");
        byte[] content = "actual".getBytes(StandardCharsets.UTF_8);
        String key = "documents/" + prefix(tenant.value()) + "/doc-1.docx";
        StoredObject reference = new StoredObject(key,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content.length, sha256("expected".getBytes(StandardCharsets.UTF_8)));
        MemoryStorage storage = new MemoryStorage();
        storage.values.put(key, content);
        try (InputStream input = new TenantObjectStorageResolver(storage).open(tenant, reference)) {
            assertThrows(RuntimeException.class, input::readAllBytes);
        }
    }

    @Test
    void deletesOnlyTenantConfinedObject() throws Exception {
        TenantId tenant = TenantId.of("tenant-a");
        String key = "documents/" + prefix(tenant.value()) + "/doc-1.docx";
        StoredObject reference = new StoredObject(key, "application/octet-stream", 1, sha256(new byte[]{1}));
        MemoryStorage storage = new MemoryStorage();
        storage.values.put(key, new byte[]{1});
        TenantObjectStorageResolver resolver = new TenantObjectStorageResolver(storage);

        resolver.delete(tenant, reference);

        assertThrows(IllegalArgumentException.class,
                () -> resolver.delete(TenantId.of("tenant-b"), reference));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.delete(tenant,
                        new StoredObject("documents/other/object", "application/octet-stream", 1,
                                sha256(new byte[]{1}))));
    }

    private static String prefix(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
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
