package com.reportai.application.task;

import com.reportai.common.id.IdGenerator;
import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionArtifactPersistenceServiceTest {
    @Test
    void persistsFilesAndExecutionLogsAsChecksummedTenantScopedReferences() throws Exception {
        Path root = Files.createTempDirectory("reportai-artifacts-");
        Path workspace = Files.createDirectories(root.resolve("task-1"));
        Files.createDirectories(workspace.resolve("plots"));
        Files.writeString(workspace.resolve("plots/loss.png"), "png-bytes", StandardCharsets.UTF_8);
        Files.writeString(workspace.resolve("plots/loss-copy.png"), "png-bytes", StandardCharsets.UTF_8);

        MemoryStorage storage = new MemoryStorage();
        RecordingIntegrity integrity = new RecordingIntegrity();
        ExecutionArtifactPersistenceService service = new ExecutionArtifactPersistenceService(
                root, storage, integrity, new FixedIds(), 1024);

        ExecutionArtifactPersistenceService.PersistedArtifacts result = service.persist(
                new TenantId("tenant-a"), "task-1", "task-1", Map.of(
                        "artifactKeys", List.of("plots/loss.png", "plots/loss-copy.png"),
                        "stdout", "completed\n",
                        "stderr", ""));

        assertEquals(2, result.artifactReferences().size());
        Map<String, Object> artifact = result.artifactReferences().get(0);
        assertEquals("plots/loss.png", artifact.get("logicalKey"));
        assertTrue(String.valueOf(artifact.get("objectKey")).matches(
                "tasks/[0-9a-f]{32}/task-1/artifacts/[0-9a-f]{64}/[0-9a-f]{64}"));
        assertNotEquals(artifact.get("objectKey"), result.artifactReferences().get(1).get("objectKey"));
        assertEquals("image/png", artifact.get("mediaType"));
        assertEquals("completed\n", new String(storage.bytes(String.valueOf(result.stdoutReference().get("objectKey"))), StandardCharsets.UTF_8));
        assertEquals(3, integrity.saved.size());
        assertEquals(3, integrity.saved.stream().map(ObjectIntegrity::role).distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> result.artifactReferences().add(Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> artifact.put("objectKey", "tampered"));
    }

    @Test
    void acceptsUnicodeAndSpacesInLogicalPathWhileKeepingStorageKeyAscii() throws Exception {
        Path root = Files.createTempDirectory("reportai-artifacts-");
        Path workspace = Files.createDirectories(root.resolve("task-1/output"));
        String logicalKey = "output/软件项目范围管理 实验报告_已完成.docx";
        Files.writeString(workspace.resolve("软件项目范围管理 实验报告_已完成.docx"), "docx-bytes",
                StandardCharsets.UTF_8);

        MemoryStorage storage = new MemoryStorage();
        RecordingIntegrity integrity = new RecordingIntegrity();
        ExecutionArtifactPersistenceService service = new ExecutionArtifactPersistenceService(
                root, storage, integrity, new FixedIds(), 1024);

        ExecutionArtifactPersistenceService.PersistedArtifacts result = service.persist(
                new TenantId("tenant-a"), "task-1", "task-1", Map.of(
                        "artifactKeys", List.of(logicalKey)));

        Map<String, Object> artifact = result.artifactReferences().get(0);
        String objectKey = String.valueOf(artifact.get("objectKey"));
        assertEquals(logicalKey, artifact.get("logicalKey"));
        assertTrue(objectKey.matches("tasks/[0-9a-f]{32}/task-1/artifacts/[0-9a-f]{64}/[0-9a-f]{64}"));
        assertFalse(objectKey.contains("软件"));
        assertFalse(objectKey.contains(" "));
    }

    @Test
    void rejectsTraversalBeforeReadingOrUploading() throws Exception {
        Path root = Files.createTempDirectory("reportai-artifacts-");
        Files.createDirectories(root.resolve("task-1"));
        MemoryStorage storage = new MemoryStorage();
        RecordingIntegrity integrity = new RecordingIntegrity();
        ExecutionArtifactPersistenceService service = new ExecutionArtifactPersistenceService(
                root, storage, integrity, new FixedIds(), 1024);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> service.persist(
                new TenantId("tenant-a"), "task-1", "task-1", Map.of("artifactKeys", List.of("../secret.txt"))));
        assertTrue(failure.getMessage().contains("artifact key"));
        assertTrue(storage.values.isEmpty());
        assertTrue(integrity.saved.isEmpty());
    }

    @Test
    void rejectsAbsoluteAndAmbiguousLogicalPaths() throws Exception {
        Path root = Files.createTempDirectory("reportai-artifacts-");
        Files.createDirectories(root.resolve("task-1"));
        MemoryStorage storage = new MemoryStorage();
        RecordingIntegrity integrity = new RecordingIntegrity();
        ExecutionArtifactPersistenceService service = new ExecutionArtifactPersistenceService(
                root, storage, integrity, new FixedIds(), 1024);

        for (String invalid : List.of("C:/secret.txt", "output\\secret.txt", "output//secret.txt",
                "output/../secret.txt", "output/./secret.txt", "output/name.txt ")) {
            assertThrows(IllegalArgumentException.class, () -> service.persist(
                    new TenantId("tenant-a"), "task-1", "task-1",
                    Map.of("artifactKeys", List.of(invalid))), invalid);
        }
        assertTrue(storage.values.isEmpty());
        assertTrue(integrity.saved.isEmpty());
    }

    @Test
    void rejectsMissingAndOversizedArtifacts() throws Exception {
        Path root = Files.createTempDirectory("reportai-artifacts-");
        Path workspace = Files.createDirectories(root.resolve("task-1"));
        Files.writeString(workspace.resolve("large.bin"), "0123456789", StandardCharsets.UTF_8);
        ExecutionArtifactPersistenceService service = new ExecutionArtifactPersistenceService(
                root, new MemoryStorage(), new RecordingIntegrity(), new FixedIds(), 4);

        assertThrows(IllegalArgumentException.class, () -> service.persist(new TenantId("tenant-a"), "task-1",
                "task-1", Map.of("artifactKeys", List.of("missing.png"))));
        assertThrows(IllegalArgumentException.class, () -> service.persist(new TenantId("tenant-a"), "task-1",
                "task-1", Map.of("artifactKeys", List.of("large.bin"))));
    }

    private static final class FixedIds implements IdGenerator {
        private int sequence;

        @Override
        public String nextId() {
            return "integrity-" + (++sequence);
        }
    }

    private static final class RecordingIntegrity implements ObjectIntegrityRepository {
        private final List<ObjectIntegrity> saved = new ArrayList<>();

        @Override
        public ObjectIntegrity save(ObjectIntegrity integrity) {
            saved.add(integrity);
            return integrity;
        }

        @Override
        public List<ObjectIntegrity> findByOwner(TenantId tenantId, String ownerType, String ownerId) {
            return saved.stream().filter(value -> value.tenantId().equals(tenantId)
                    && value.ownerType().equals(ownerType) && value.ownerId().equals(ownerId)).toList();
        }

        @Override
        public void deleteByOwner(TenantId tenantId, String ownerType, String ownerId) {
            saved.removeIf(value -> value.tenantId().equals(tenantId)
                    && value.ownerType().equals(ownerType) && value.ownerId().equals(ownerId));
        }
    }

    private static final class MemoryStorage implements ObjectStorage {
        private final Map<String, byte[]> values = new HashMap<>();

        @Override
        public String put(String objectKey, InputStream data, long contentLength, String contentType) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                data.transferTo(output);
                if (output.size() != contentLength) throw new AssertionError("declared size mismatch");
                values.put(objectKey, output.toByteArray());
                return objectKey;
            } catch (IOException failure) {
                throw new AssertionError(failure);
            }
        }

        @Override
        public InputStream open(String objectKey) {
            byte[] bytes = values.get(objectKey);
            if (bytes == null) throw new IllegalArgumentException("missing object");
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void delete(String objectKey) {
            values.remove(objectKey);
        }

        private byte[] bytes(String objectKey) {
            return values.get(objectKey);
        }
    }
}
