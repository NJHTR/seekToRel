package com.reportai.application.storage;

import com.reportai.application.config.properties.ReportAiObjectStorageProperties;
import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityReconciliationRepository;
import com.reportai.storage.ObjectStorageResolver;
import com.reportai.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectIntegrityReconcilerTest {
    @Test
    void verifiesBoundedBatchAndRecordsStatus() {
        FakeRepository repository = new FakeRepository();
        repository.entries.add(new ObjectIntegrity("integrity-1", TenantId.of("tenant-a"),
                "DOCUMENT", "document-1", "SOURCE",
                new StoredObject("documents/" + prefix("tenant-a") + "/source.docx",
                        "application/octet-stream", 3, sha256("abc")), Instant.now()));
        ObjectStorageResolver resolver = (tenant, reference) ->
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
        ReportAiObjectStorageProperties properties = new ReportAiObjectStorageProperties();
        properties.setReconciliationBatchSize(1);

        try (ObjectIntegrityReconciler reconciler = new ObjectIntegrityReconciler(repository, resolver, properties)) {
            assertEquals(1, reconciler.reconcileNow());
        }
        assertEquals(List.of("integrity-1"), repository.verified);
        assertEquals(0, repository.failures.size());
    }

    @Test
    void deletesOnlyAgedOrphansAfterPhysicalDeletion() {
        FakeRepository repository = new FakeRepository();
        ObjectIntegrity orphan = new ObjectIntegrity("orphan-1", TenantId.of("tenant-a"),
                "DOCUMENT", "deleted-document", "SOURCE",
                new StoredObject("documents/" + prefix("tenant-a") + "/orphan.docx",
                        "application/octet-stream", 3, sha256("abc")),
                Instant.now().minus(Duration.ofDays(8)));
        repository.orphans.add(orphan);
        DeletingResolver resolver = new DeletingResolver();
        ReportAiObjectStorageProperties properties = new ReportAiObjectStorageProperties();
        properties.setOrphanRetention(Duration.ofDays(7));

        try (ObjectIntegrityReconciler reconciler = new ObjectIntegrityReconciler(repository, resolver, properties)) {
            reconciler.reconcileNow();
        }

        assertEquals(List.of(orphan.object().objectKey()), resolver.deletedKeys);
        assertEquals(List.of(orphan.id()), repository.deletedMetadataIds);
    }

    private static String prefix(String value) {
        return digest(value).substring(0, 32);
    }

    private static String sha256(String value) {
        return digest(value);
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeRepository implements ObjectIntegrityReconciliationRepository {
        private final List<ObjectIntegrity> entries = new ArrayList<>();
        private final List<ObjectIntegrity> orphans = new ArrayList<>();
        private final List<String> verified = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final List<String> deletedMetadataIds = new ArrayList<>();

        @Override
        public List<ObjectIntegrity> findForVerification(int limit) {
            return entries.stream().limit(limit).toList();
        }

        @Override
        public List<ObjectIntegrity> findOrphanedMetadata(int limit, Instant olderThan) {
            return orphans.stream().limit(limit).toList();
        }

        @Override
        public int deleteMetadataByIds(List<String> ids) {
            deletedMetadataIds.addAll(ids);
            return ids.size();
        }

        @Override
        public void markVerified(String id, Instant verifiedAt) {
            verified.add(id);
        }

        @Override
        public void markVerificationFailed(String id, Instant failedAt, String error) {
            failures.add(id);
        }
    }

    private static final class DeletingResolver implements ObjectStorageResolver {
        private final List<String> deletedKeys = new ArrayList<>();

        @Override
        public java.io.InputStream open(TenantId tenantId, StoredObject reference) {
            return new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void delete(TenantId tenantId, StoredObject reference) {
            deletedKeys.add(reference.objectKey());
        }
    }
}
