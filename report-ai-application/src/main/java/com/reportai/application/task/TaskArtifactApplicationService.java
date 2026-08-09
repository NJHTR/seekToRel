package com.reportai.application.task;

import com.reportai.auth.model.Permission;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ArtifactStorageResolver;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.StoredObject;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Tenant-authorized access to persisted execution artifacts; clients never submit storage keys. */
@Service
public final class TaskArtifactApplicationService {
    private static final String OWNER_TYPE = "TASK";
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;

    private final AuthorizationPort authorization;
    private final ReportTaskRepository tasks;
    private final ObjectIntegrityRepository integrity;
    private final Optional<ArtifactStorageResolver> resolver;

    public TaskArtifactApplicationService(AuthorizationPort authorization,
                                          ReportTaskRepository tasks,
                                          ObjectIntegrityRepository integrity,
                                          Optional<ArtifactStorageResolver> resolver) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.integrity = Objects.requireNonNull(integrity, "integrity");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public List<ArtifactMetadata> list(String taskId) {
        authorization.requireAuthenticated();
        authorization.requirePermission(Permission.EXECUTION_READ);
        TenantId tenant = TenantContext.require();
        String valid = validTaskId(taskId);
        requireTask(tenant, valid);
        return integrity.findByOwner(tenant, OWNER_TYPE, valid).stream()
                .filter(value -> value.role().startsWith("ARTIFACT_")
                        || value.role().startsWith("EXECUTION_"))
                .map(value -> new ArtifactMetadata(value.role(), value.object().objectKey(),
                        value.object().mediaType(), value.object().size(), value.object().sha256()))
                .toList();
    }

    public Download download(String taskId, String artifactId) {
        authorization.requireAuthenticated();
        authorization.requirePermission(Permission.EXECUTION_READ);
        TenantId tenant = TenantContext.require();
        String validTask = validTaskId(taskId);
        String validArtifact = validArtifactId(artifactId);
        requireTask(tenant, validTask);
        ObjectIntegrity record = integrity.findByOwner(tenant, OWNER_TYPE, validTask).stream()
                .filter(value -> validArtifact.equals(value.role()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "artifact not found"));
        StoredObject reference = record.object();
        if (reference.size() < 1 || reference.size() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "artifact exceeds the maximum size");
        }
        ArtifactStorageResolver storage = resolver.orElseThrow(() ->
                new BusinessException(ErrorCode.INTERNAL_ERROR, "artifact storage is not configured"));
        try (InputStream input = storage.open(tenant, reference)) {
            return new Download(reference, readBounded(input, reference.size()));
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof BusinessException business) throw business;
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "artifact could not be read");
        }
    }

    private void requireTask(TenantId tenant, String taskId) {
        if (tasks.findById(tenant, taskId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "task not found");
        }
    }

    private static byte[] readBounded(InputStream input, long expected) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) expected);
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            if (output.size() > MAX_BYTES - read) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "artifact exceeds the maximum size");
            }
            output.write(buffer, 0, read);
        }
        if (output.size() != expected) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "artifact is incomplete");
        }
        return output.toByteArray();
    }

    private static String validTaskId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "taskId is invalid");
        }
        return value;
    }

    private static String validArtifactId(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_-]{0,31}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "artifactId is invalid");
        }
        return value;
    }

    public record ArtifactMetadata(String id, String objectKey, String mediaType, long size, String sha256) {}

    public record Download(StoredObject reference, byte[] bytes) {
        public Download {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
