package com.reportai.application.task;

import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ArtifactStorageResolver;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.StoredObject;
import com.reportai.storage.objectstore.TenantArtifactStorageResolver;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskInputApplicationServiceTest {
    private static final TenantId TENANT = new TenantId("tenant-a");
    private static final String TASK = "task-1";
    private final MemoryStorage storage = new MemoryStorage();
    private final MemoryIntegrity integrity = new MemoryIntegrity();
    private final ReportTask task = new ReportTask(TASK, TENANT, "documents/a/report.docx",
            ReportTaskStatus.CREATED, 0, Instant.now(), Instant.now());
    private final TaskInputApplicationService service = new TaskInputApplicationService(
            new AllowAuthorization(TENANT), new SingleTaskRepository(task), integrity,
            provider(storage), provider(new TenantArtifactStorageResolver(storage)),
            new FixedIds(), 1024 * 1024);

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void storesTenantScopedInputAndMaterializesVerifiedBytes() throws Exception {
        TenantContext.set(TENANT);
        byte[] bytes = "real-dataset".getBytes(StandardCharsets.UTF_8);
        TaskInputApplicationService.InputMetadata metadata = service.upload(TASK, "data/train.csv",
                new MockMultipartFile("file", "train.csv", "text/csv", bytes));

        assertEquals("data/train.csv", metadata.relativePath());
        assertEquals("application/octet-stream", metadata.mediaType());
        Path root = Files.createTempDirectory("reportai-input-");
        Files.createDirectories(root.resolve(TASK).resolve("input").resolve("data"));
        Files.writeString(root.resolve(TASK).resolve("input").resolve("stale.txt"), "stale");
        TaskInputApplicationService.Materialization result = service.materialize(TENANT, TASK, TASK, root);

        assertArrayEquals(bytes, Files.readAllBytes(root.resolve(TASK).resolve("input/data/train.csv")));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(root.resolve(TASK).resolve("input/stale.txt")));
        assertEquals(metadata.sha256(), result.inputs().getFirst().sha256());
        assertEquals(64, result.inputDigest().length());
    }

    @Test
    void rejectsCrossTenantObjectAndUnsafePath() {
        TenantContext.set(TENANT);
        assertThrows(RuntimeException.class, () -> service.upload(TASK, "../secret.bin",
                new MockMultipartFile("file", "secret.bin", "application/octet-stream", new byte[]{1})));
    }

    @Test
    void reservesCanonicalSourceDocumentMountPath() {
        TenantContext.set(TENANT);
        assertThrows(RuntimeException.class, () -> service.upload(TASK, "source-document.docx",
                new MockMultipartFile("file", "source-document.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1})));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            public T getObject(Object... args) { return value; }
            public T getIfAvailable() { return value; }
            public T getIfUnique() { return value; }
            public java.util.Iterator<T> iterator() { return List.of(value).iterator(); }
        };
    }

    private static final class AllowAuthorization implements AuthorizationPort {
        private final TenantPrincipal principal;
        private AllowAuthorization(TenantId tenant) {
            principal = TenantPrincipal.authenticated("user", tenant,
                    Set.of(Permission.TASK_CREATE, Permission.TASK_READ), Instant.now().plusSeconds(3600), "token");
        }
        public TenantPrincipal requireAuthenticated() { return principal; }
        public void requirePermission(Permission permission) { if (!principal.hasPermission(permission)) throw new SecurityException(); }
        public void requireAnyPermission(Iterable<Permission> permissions) { }
        public void requireTenant(TenantId tenantId) { }
        public boolean isAllowed(Permission permission) { return principal.hasPermission(permission); }
    }

    private static final class SingleTaskRepository implements ReportTaskRepository {
        private final ReportTask task;
        private SingleTaskRepository(ReportTask task) { this.task = task; }
        public ReportTask save(ReportTask value) { return value; }
        public boolean update(ReportTask value, long version) { return true; }
        public Optional<ReportTask> findById(TenantId tenant, String id) {
            return task.tenantId().equals(tenant) && task.id().equals(id) ? Optional.of(task) : Optional.empty();
        }
    }

    private static final class FixedIds implements IdGenerator {
        private int sequence;
        public String nextId() { return "input-id-" + (++sequence); }
    }

    private static final class MemoryIntegrity implements ObjectIntegrityRepository {
        private final List<ObjectIntegrity> values = new ArrayList<>();
        public ObjectIntegrity save(ObjectIntegrity value) { values.removeIf(existing -> existing.tenantId().equals(value.tenantId())
                && existing.ownerType().equals(value.ownerType()) && existing.ownerId().equals(value.ownerId())
                && existing.role().equals(value.role())); values.add(value); return value; }
        public List<ObjectIntegrity> findByOwner(TenantId tenant, String type, String owner) {
            return values.stream().filter(value -> value.tenantId().equals(tenant)
                    && value.ownerType().equals(type) && value.ownerId().equals(owner)).toList();
        }
        public void deleteByOwner(TenantId tenant, String type, String owner) { values.removeIf(value -> value.tenantId().equals(tenant)
                && value.ownerType().equals(type) && value.ownerId().equals(owner)); }
    }

    private static final class MemoryStorage implements ObjectStorage {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();
        public String put(String key, InputStream input, long length, String contentType) {
            try { byte[] bytes = input.readAllBytes(); if (bytes.length != length) throw new IllegalArgumentException();
                values.put(key, bytes); return key; } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        }
        public InputStream open(String key) { return new ByteArrayInputStream(values.getOrDefault(key, new byte[0])); }
        public void delete(String key) { values.remove(key); }
    }
}
