package com.reportai.application.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.api.report.CreateTaskRequest;
import com.reportai.api.report.TaskControlRequest;
import com.reportai.api.report.TaskResponse;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.Document;
import com.reportai.storage.DocumentRepository;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.scheduler.QueuedTask;
import com.reportai.scheduler.SchedulerOutboxRepository;
import org.springframework.transaction.annotation.Transactional;
import com.reportai.task.CreateReportTaskCommand;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import com.reportai.task.TaskApplicationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-facing task orchestration boundary. It never reaches into a Mapper.
 * Spring proxies this service for the transactional create operation, so the
 * concrete class must remain non-final for CGLIB proxy generation.
 */
@Service
public class TaskHttpApplicationService {
    private final TaskApplicationService taskService;
    private final ReportTaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final ObjectIntegrityRepository objectIntegrityRepository;
    private final AuthorizationPort authorization;
    private final ObjectMapper objectMapper;
    private final SchedulerOutboxRepository schedulerOutbox;
    private final ReportAiSchedulerProperties schedulerProperties;

    public TaskHttpApplicationService(TaskApplicationService taskService,
                                      ReportTaskRepository taskRepository,
                                      DocumentRepository documentRepository,
                                      ObjectIntegrityRepository objectIntegrityRepository,
                                      AuthorizationPort authorization,
                                      ObjectMapper objectMapper,
                                      SchedulerOutboxRepository schedulerOutbox,
                                      ReportAiSchedulerProperties schedulerProperties) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.objectIntegrityRepository = objectIntegrityRepository;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
        this.schedulerOutbox = schedulerOutbox;
        this.schedulerProperties = schedulerProperties;
    }

    @Transactional
    public TaskResponse create(@Valid CreateTaskRequest request, String idempotencyHeader) {
        TenantPrincipal principal = authorized(Permission.TASK_CREATE);
        if (idempotencyHeader == null || idempotencyHeader.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key header is required");
        }
        DocumentReference reference = resolveDocumentReference(principal.tenantId(), request);
        ReportTask task = taskService.create(new CreateReportTaskCommand(principal.tenantId(),
                reference.objectKey(), new IdempotencyKey(idempotencyHeader)));
        enqueue(task, reference, idempotencyHeader);
        return toResponse(task);
    }

    public TaskResponse get(String taskId) {
        TenantId tenantId = authorized(Permission.TASK_READ).tenantId();
        return toResponse(findTask(tenantId, taskId));
    }

    public TaskResponse changeStatus(String taskId, TaskControlRequest request) {
        TenantId tenantId = authorized(Permission.TASK_CONTROL).tenantId();
        if (request == null || request.expectedVersion() < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "expectedVersion must not be negative");
        }
        if (request.targetStatus() == null || request.targetStatus().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "targetStatus is required");
        }
        ReportTask current = findTask(tenantId, taskId);
        ReportTaskStatus targetStatus;
        try {
            targetStatus = ReportTaskStatus.valueOf(request.targetStatus());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "targetStatus is invalid");
        }
        ReportTask next = current.transitionTo(targetStatus);
        if (!taskRepository.update(next, request.expectedVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "task version conflict");
        }
        return toResponse(next);
    }

    private ReportTask findTask(TenantId tenantId, String taskId) {
        validateId(taskId, "taskId");
        return taskRepository.findById(tenantId, taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task not found"));
    }

    private DocumentReference resolveDocumentReference(TenantId tenantId, CreateTaskRequest request) {
        if (request == null || !request.hasDocumentReference()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "documentId or documentObjectKey is required");
        }
        if (request.documentId() != null && !request.documentId().isBlank()) {
            validateId(request.documentId(), "documentId");
            Document document = documentRepository.findById(tenantId, request.documentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "document not found"));
            ObjectIntegrity source = objectIntegrityRepository.findByOwner(tenantId, "DOCUMENT", document.id())
                    .stream().filter(item -> "SOURCE".equals(item.role())).findFirst().orElse(null);
            return new DocumentReference(document.id(), document.sourceObjectKey(),
                    source == null ? null : source.object());
        }
        // A raw object key has no trusted media type/checksum/document ownership
        // metadata. Tasks must reference a parsed document so the worker can verify
        // the source before opening it; callers should upload first and use documentId.
        throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                "documentId is required for task execution");
    }

    private void enqueue(ReportTask task, DocumentReference reference, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.id());
        payload.put("tenantId", task.tenantId().value());
        payload.put("documentId", reference.documentId());
        payload.put("documentObjectKey", reference.objectKey());
        if (reference.source() != null) {
            payload.put("mediaType", reference.source().mediaType());
            payload.put("size", reference.source().size());
            payload.put("sha256", reference.source().sha256());
        }
        final String serialized;
        try {
            serialized = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "task payload could not be created");
        }
        schedulerOutbox.enqueue(new QueuedTask(task.id(), task.tenantId(), "report-task", serialized,
                idempotencyKey, schedulerProperties.reportTaskRetryPolicy(), Instant.now()));
    }

    private record DocumentReference(String documentId, String objectKey, com.reportai.storage.StoredObject source) {
    }

    private TenantPrincipal authorized(Permission permission) {
        TenantPrincipal principal = authorization.requireAuthenticated();
        TenantId contextTenant = TenantContext.require();
        if (!principal.tenantId().equals(contextTenant)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "tenant context is invalid");
        }
        authorization.requirePermission(permission);
        return principal;
    }

    private static TaskResponse toResponse(ReportTask task) {
        return new TaskResponse(task.id(), task.status().name(), task.version());
    }

    private static void validateId(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " is invalid");
        }
    }
}
