package com.reportai.application.task;

import com.reportai.auth.model.Permission;
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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the immutable DOCX produced by a successful report task.
 *
 * <p>The document export endpoint is intentionally separate: it rebuilds a
 * canonical document, while this service returns the review-gated artifact
 * persisted by {@link ReportTaskHandler}. The integrity repository remains the
 * source of the object key, size and checksum; callers never submit a storage
 * path.</p>
 */
@Service
public final class TaskExportApplicationService {
    public static final String OUTPUT_ROLE = "OUTPUT";
    public static final String TASK_OWNER_TYPE = "TASK";
    public static final int MAX_EXPORT_BYTES = 50 * 1024 * 1024;
    private static final int COPY_BUFFER_SIZE = 8192;

    private final AuthorizationPort authorization;
    private final ReportTaskRepository tasks;
    private final ObjectIntegrityRepository objectIntegrity;
    private final Optional<ObjectStorageResolver> storageResolver;

    public TaskExportApplicationService(AuthorizationPort authorization,
                                        ReportTaskRepository tasks,
                                        ObjectIntegrityRepository objectIntegrity,
                                        Optional<ObjectStorageResolver> storageResolver) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.objectIntegrity = Objects.requireNonNull(objectIntegrity, "objectIntegrity");
        this.storageResolver = Objects.requireNonNull(storageResolver, "storageResolver");
    }

    /**
     * Returns the bounded, checksum-verified bytes for a completed task.
     * Authorization and tenant ownership are checked before any object is opened.
     */
    public byte[] export(String taskId) {
        authorization.requireAuthenticated();
        authorization.requirePermission(Permission.EXPORT_READ);
        TenantId tenantId = TenantContext.require();
        String validTaskId = validTaskId(taskId);
        ReportTask task = tasks.findById(tenantId, validTaskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task not found"));
        if (task.status() != ReportTaskStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.CONFLICT, "task export is not ready");
        }
        ObjectIntegrity output = objectIntegrity.findByOwner(tenantId, TASK_OWNER_TYPE, validTaskId).stream()
                .filter(candidate -> OUTPUT_ROLE.equals(candidate.role()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "completed task output is unavailable"));
        StoredObject reference = output.object();
        if (reference.size() > MAX_EXPORT_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "export exceeds the maximum size");
        }
        ObjectStorageResolver resolver = storageResolver.orElseThrow(() ->
                new BusinessException(ErrorCode.INTERNAL_ERROR, "object storage is not configured"));
        try (InputStream input = resolver.open(tenantId, reference)) {
            return readBounded(input, reference.size());
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof BusinessException business) {
                throw business;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "completed task output could not be read");
        }
    }

    private static byte[] readBounded(InputStream input, long expectedSize) throws IOException {
        if (expectedSize < 1 || expectedSize > MAX_EXPORT_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "export exceeds the maximum size");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) expectedSize);
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            if (output.size() > MAX_EXPORT_BYTES - read) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "export exceeds the maximum size");
            }
            output.write(buffer, 0, read);
        }
        if (output.size() != expectedSize) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "completed task output is incomplete");
        }
        return output.toByteArray();
    }

    private static String validTaskId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "taskId is invalid");
        }
        return value;
    }
}
