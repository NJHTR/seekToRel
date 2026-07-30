package com.reportai.storage.objectstore;

import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.ObjectStorageResolver;
import com.reportai.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Tenant-confined object reader. The returned stream verifies byte count and SHA-256 as it is
 * consumed, so callers cannot accidentally process a swapped or truncated object.
 */
public final class TenantObjectStorageResolver implements ObjectStorageResolver {
    private static final int BUFFER_SIZE = 8192;
    private final ObjectStorage storage;

    public TenantObjectStorageResolver(ObjectStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public InputStream open(TenantId tenantId, StoredObject reference) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(reference, "reference");
        String key = tenantKey(tenantId, reference);
        return new VerifyingInputStream(storage.open(key), reference);
    }

    @Override
    public void delete(TenantId tenantId, StoredObject reference) {
        storage.delete(tenantKey(tenantId, reference));
    }

    private static String tenantKey(TenantId tenantId, StoredObject reference) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(reference, "reference");
        String tenantPrefix = "documents/" + digestPrefix(tenantId.value()) + "/";
        String key = ObjectKeyValidator.validate(reference.objectKey());
        if (!key.startsWith(tenantPrefix)) {
            throw new IllegalArgumentException("object reference does not belong to tenant");
        }
        return key;
    }

    private static String digestPrefix(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class VerifyingInputStream extends InputStream {
        private final InputStream delegate;
        private final StoredObject reference;
        private final MessageDigest digest;
        private long count;
        private boolean verified;
        private boolean failed;
        private boolean closed;

        private VerifyingInputStream(InputStream delegate, StoredObject reference) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.reference = reference;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        @Override
        public int read() throws IOException {
            ensureOpen();
            int value = delegate.read();
            if (value < 0) {
                verifyAtEnd();
                return -1;
            }
            update(1);
            digest.update((byte) value);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            ensureOpen();
            int read = delegate.read(bytes, offset, length);
            if (read < 0) {
                verifyAtEnd();
                return -1;
            }
            if (read > 0) {
                update(read);
                digest.update(bytes, offset, read);
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
            long skipped = 0;
            byte[] buffer = new byte[(int) Math.min(BUFFER_SIZE, Math.max(1L, count))];
            while (skipped < count) {
                int wanted = (int) Math.min(buffer.length, count - skipped);
                int read = read(buffer, 0, wanted);
                if (read < 0) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (!verified && !failed && count != reference.size()) {
                    throw new ObjectStorageException("object stream was closed before integrity verification");
                }
                if (!verified && !failed) {
                    verifyAtEnd();
                }
            } finally {
                delegate.close();
            }
        }

        private void update(int amount) {
            if (amount > reference.size() - count) {
                failed = true;
                throw new ObjectStorageException("object exceeds its declared size");
            }
            count += amount;
        }

        private void verifyAtEnd() {
            if (verified) {
                return;
            }
            if (count != reference.size()) {
                failed = true;
                throw new ObjectStorageException("object size does not match its reference");
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!reference.sha256().equals(actual)) {
                failed = true;
                throw new ObjectStorageException("object checksum does not match its reference");
            }
            verified = true;
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("object stream is closed");
            }
        }
    }
}
