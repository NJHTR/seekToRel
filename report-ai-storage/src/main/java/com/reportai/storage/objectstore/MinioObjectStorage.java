package com.reportai.storage.objectstore;

import com.reportai.storage.ObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * MinIO Java SDK adapter for MinIO and S3-compatible endpoints.
 *
 * <p>Uploads and downloads are streamed. The adapter never buffers an object
 * in memory and all externally supplied keys and metadata are validated before
 * invoking the provider.</p>
 */
public final class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final ObjectStorageProperties properties;

    public MinioObjectStorage(MinioClient client, ObjectStorageProperties properties) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /** Creates an adapter for a MinIO or S3-compatible endpoint. */
    public static MinioObjectStorage create(ObjectStorageProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey());
        if (!properties.region().isBlank()) {
            builder.region(properties.region());
        }
        return new MinioObjectStorage(builder.build(), properties);
    }

    /**
     * Ensures the configured bucket exists. This is intentionally explicit:
     * production deployments can provision buckets through IaC and leave this
     * disabled, while local/pilot profiles may opt in to self-provisioning.
     */
    public void ensureBucketExists() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.bucket())
                    .build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.bucket())
                        .build());
            }
        } catch (Exception exception) {
            throw new ObjectStorageException("Object storage bucket is unavailable", exception);
        }
    }

    @Override
    public String put(String objectKey, InputStream data, long contentLength, String contentType) {
        String validatedKey = ObjectKeyValidator.validate(objectKey);
        String validatedType = MediaTypeValidator.validate(contentType);
        if (contentLength <= 0 || contentLength > properties.maxObjectSizeBytes()) {
            throw new IllegalArgumentException("contentLength is outside the configured limit");
        }
        Objects.requireNonNull(data, "data must not be null");

        CountingInputStream counting = new CountingInputStream(data, contentLength);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(validatedKey)
                    .stream(counting, contentLength, -1)
                    .contentType(validatedType)
                    .build());
            if (counting.bytesRead() != contentLength) {
                throw new ObjectStorageException("Object upload was shorter than declared content length");
            }
            return validatedKey;
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("Object upload failed", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        String validatedKey = ObjectKeyValidator.validate(objectKey);
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(validatedKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Object download failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        String validatedKey = ObjectKeyValidator.validate(objectKey);
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(validatedKey)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Object deletion failed", exception);
        }
    }

    /** Counts bytes consumed by the SDK while enforcing the declared length. */
    private static final class CountingInputStream extends FilterInputStream {
        private final long expectedLength;
        private long bytesRead;

        private CountingInputStream(InputStream delegate, long expectedLength) {
            super(delegate);
            this.expectedLength = expectedLength;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(int amount) throws IOException {
            if (bytesRead > expectedLength - amount) {
                throw new IOException("stream exceeded declared content length");
            }
            bytesRead += amount;
        }

        private long bytesRead() {
            return bytesRead;
        }
    }
}
