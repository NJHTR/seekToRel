package com.reportai.application.task;

import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorageResolver;
import com.reportai.storage.StoredObject;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskExportApplicationServiceTest {
    private static final TenantId TENANT = TenantId.of("tenant-a");
    private static final String TASK_ID = "task-1";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void returnsOnlyTheStoredOutputForACompletedTenantTask() throws Exception {
        byte[] expected = "final-docx-bytes".getBytes(StandardCharsets.UTF_8);
        StoredObject reference = reference(expected);
        InMemoryTasks tasks = new InMemoryTasks();
        tasks.save(task(ReportTaskStatus.SUCCEEDED));
        InMemoryIntegrity integrity = new InMemoryIntegrity();
        integrity.save(new ObjectIntegrity("integrity-1", TENANT, "TASK", TASK_ID, "OUTPUT",
                reference, Instant.now()));
        TenantContext.set(TENANT);

        byte[] actual = service(tasks, integrity, new InMemoryResolver(reference.objectKey(), expected)).export(TASK_ID);

        assertArrayEquals(expected, actual);
    }

    @Test
    void refusesExportBeforeTaskSucceeds() {
        InMemoryTasks tasks = new InMemoryTasks();
        tasks.save(task(ReportTaskStatus.EXECUTING));
        TenantContext.set(TENANT);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service(tasks, new InMemoryIntegrity(), new InMemoryResolver("unused", new byte[]{1}))
                        .export(TASK_ID));

        assertEquals(ErrorCode.CONFLICT, failure.getErrorCode());
    }

    @Test
    void keepsMissingOrTamperedOutputFailClosed() throws Exception {
        byte[] expected = "final-docx-bytes".getBytes(StandardCharsets.UTF_8);
        StoredObject reference = reference(expected);
        InMemoryTasks tasks = new InMemoryTasks();
        tasks.save(task(ReportTaskStatus.SUCCEEDED));
        InMemoryIntegrity integrity = new InMemoryIntegrity();
        integrity.save(new ObjectIntegrity("integrity-1", TENANT, "TASK", TASK_ID, "OUTPUT",
                reference, Instant.now()));
        TenantContext.set(TENANT);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service(tasks, integrity, new InMemoryResolver(reference.objectKey(), "tampered".getBytes(StandardCharsets.UTF_8)))
                        .export(TASK_ID));

        assertEquals(ErrorCode.INTERNAL_ERROR, failure.getErrorCode());
    }

    private static TaskExportApplicationService service(InMemoryTasks tasks, InMemoryIntegrity integrity,
                                                        ObjectStorageResolver resolver) {
        return new TaskExportApplicationService(new FixedAuthorization(TENANT), tasks, integrity,
                Optional.of(resolver));
    }

    private static ReportTask task(ReportTaskStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ReportTask(TASK_ID, TENANT, "documents/tenant-a/source.docx", status, 1, now, now);
    }

    private static StoredObject reference(byte[] content) throws Exception {
        return new StoredObject("documents/tenant-a/tasks/" + TASK_ID + "/output.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content.length,
                java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
    }

    private static final class InMemoryTasks implements ReportTaskRepository {
        private final Map<String, ReportTask> values = new HashMap<>();

        @Override
        public ReportTask save(ReportTask task) {
            values.put(task.tenantId().value() + ":" + task.id(), task);
            return task;
        }

        @Override
        public boolean update(ReportTask task, long expectedVersion) {
            return false;
        }

        @Override
        public Optional<ReportTask> findById(TenantId tenantId, String taskId) {
            return Optional.ofNullable(values.get(tenantId.value() + ":" + taskId));
        }
    }

    private static final class InMemoryIntegrity implements ObjectIntegrityRepository {
        private final List<ObjectIntegrity> values = new ArrayList<>();

        @Override
        public ObjectIntegrity save(ObjectIntegrity integrity) {
            values.add(integrity);
            return integrity;
        }

        @Override
        public List<ObjectIntegrity> findByOwner(TenantId tenantId, String ownerType, String ownerId) {
            return values.stream().filter(value -> value.tenantId().equals(tenantId)
                    && value.ownerType().equals(ownerType) && value.ownerId().equals(ownerId)).toList();
        }

        @Override
        public void deleteByOwner(TenantId tenantId, String ownerType, String ownerId) {
            values.removeIf(value -> value.tenantId().equals(tenantId)
                    && value.ownerType().equals(ownerType) && value.ownerId().equals(ownerId));
        }
    }

    private record InMemoryResolver(String key, byte[] content) implements ObjectStorageResolver {
        @Override
        public InputStream open(TenantId tenantId, StoredObject reference) {
            if (!key.equals(reference.objectKey())) {
                throw new IllegalArgumentException("unexpected object key");
            }
            return new ByteArrayInputStream(content);
        }
    }

    private static final class FixedAuthorization implements AuthorizationPort {
        private final TenantPrincipal principal;

        private FixedAuthorization(TenantId tenant) {
            principal = TenantPrincipal.authenticated("subject-1", tenant, Set.of(Permission.EXPORT_READ),
                    Instant.now().plusSeconds(3600), "token-1");
        }

        @Override
        public TenantPrincipal requireAuthenticated() {
            return principal;
        }

        @Override
        public void requirePermission(Permission permission) {
            if (!principal.hasPermission(permission)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "permission denied");
            }
        }

        @Override
        public void requireAnyPermission(Iterable<Permission> permissions) {
            for (Permission permission : permissions) {
                if (principal.hasPermission(permission)) {
                    return;
                }
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "permission denied");
        }

        @Override
        public void requireTenant(TenantId targetTenantId) {
            if (!principal.tenantId().equals(targetTenantId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "tenant denied");
            }
        }

        @Override
        public boolean isAllowed(Permission permission) {
            return principal.hasPermission(permission);
        }
    }
}
