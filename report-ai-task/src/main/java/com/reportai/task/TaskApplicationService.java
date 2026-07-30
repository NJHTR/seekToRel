package com.reportai.task;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.idempotency.IdempotencyRecord;
import com.reportai.common.idempotency.IdempotencyStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

public final class TaskApplicationService {
    private static final String CREATE_OPERATION = "report-task:create";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ReportTaskRepository taskRepository;
    private final IdempotencyStore idempotencyStore;
    private final IdGenerator idGenerator;

    public TaskApplicationService(ReportTaskRepository taskRepository, IdempotencyStore idempotencyStore,
                                  IdGenerator idGenerator) {
        this.taskRepository = taskRepository;
        this.idempotencyStore = idempotencyStore;
        this.idGenerator = idGenerator;
    }

    public ReportTask create(CreateReportTaskCommand command) {
        String requestHash = sha256(command.tenantId().value() + "\n" + command.documentObjectKey());
        var existing = idempotencyStore.find(command.tenantId(), CREATE_OPERATION, command.idempotencyKey());
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), requestHash);
            return taskRepository.findById(command.tenantId(), existing.get().resourceId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "idempotent task is not available yet"));
        }

        String taskId = idGenerator.nextId();
        IdempotencyRecord reservation = new IdempotencyRecord(command.tenantId(), CREATE_OPERATION,
                command.idempotencyKey(), requestHash, taskId, IdempotencyRecord.Status.RESERVED,
                Instant.now().plus(IDEMPOTENCY_TTL));
        if (!idempotencyStore.reserve(reservation)) {
            return loadExisting(command, requestHash);
        }

        Instant now = Instant.now();
        ReportTask task = new ReportTask(taskId, command.tenantId(), command.documentObjectKey(),
                ReportTaskStatus.CREATED, 0, now, now);
        try {
            ReportTask saved = taskRepository.save(task);
            idempotencyStore.complete(command.tenantId(), CREATE_OPERATION, command.idempotencyKey());
            return saved;
        } catch (RuntimeException exception) {
            idempotencyStore.release(command.tenantId(), CREATE_OPERATION, command.idempotencyKey());
            throw exception;
        }
    }

    private static void ensureSameRequest(IdempotencyRecord existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    private ReportTask loadExisting(CreateReportTaskCommand command, String requestHash) {
        IdempotencyRecord existing = idempotencyStore.find(command.tenantId(), CREATE_OPERATION,
                        command.idempotencyKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "concurrent idempotency reservation is unavailable"));
        ensureSameRequest(existing, requestHash);
        return taskRepository.findById(command.tenantId(), existing.resourceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "idempotent task is not available yet"));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
