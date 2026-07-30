package com.reportai.application.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.agent.Agent;
import com.reportai.agent.AgentContext;
import com.reportai.agent.AgentPipelineExecutor;
import com.reportai.agent.AgentPipelineResult;
import com.reportai.agent.AgentPipelineStatus;
import com.reportai.agent.CoderAgent;
import com.reportai.agent.DocumentAgent;
import com.reportai.agent.PlannerAgent;
import com.reportai.agent.ReviewerAgent;
import com.reportai.agent.RuntimeAgent;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentAsset;
import com.reportai.llm.LlmProvider;
import com.reportai.parser.DocumentParserPort;
import com.reportai.parser.ParserRequest;
import com.reportai.parser.ParserResult;
import com.reportai.scheduler.TaskExecutionContext;
import com.reportai.scheduler.TaskHandler;
import com.reportai.storage.DocumentRepository;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.ObjectStorageResolver;
import com.reportai.storage.StoredObject;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskPayload;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import com.reportai.tool.ToolRegistry;
import com.reportai.word.AssetResolver;
import com.reportai.word.ReleaseResult;
import com.reportai.word.WordBuildOptions;
import com.reportai.word.WordReleaseService;
import com.reportai.workflow.WorkflowEngine;
import com.reportai.workflow.WorkflowInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Default durable handler for scheduler type {@value #TYPE}.  The handler is the
 * application boundary between the generic scheduler and the report domain: it
 * validates the tenant-scoped payload, resumes the report aggregate with CAS
 * transitions, invokes parser/agent/tool ports, and performs the review-gated DOCX
 * release.  It never executes a host process or trusts a client supplied path.
 */
@Component
public final class ReportTaskHandler implements TaskHandler {
    public static final String TYPE = "report-task";
    private static final String DEFAULT_WORKFLOW = "report";
    private static final int MAX_PAYLOAD_FIELDS = 32;
    private static final int MAX_TRANSITION_RETRIES = 4;
    private static final int MAX_EXPORT_BYTES = 50 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ReportTaskRepository tasks;
    private final DocumentRepository documents;
    private final ObjectIntegrityRepository objectIntegrity;
    private final ObjectProvider<ObjectStorage> objectStorages;
    private final List<DocumentParserPort> parsers;
    private final ObjectProvider<ObjectStorageResolver> storageResolvers;
    private final AgentPipelineExecutor pipelineExecutor;
    private final ObjectProvider<LlmProvider> llmProviders;
    private final ObjectProvider<ToolRegistry> toolRegistries;
    private final ObjectProvider<WorkflowEngine> workflows;
    private final WordReleaseService wordRelease;
    private final IdGenerator ids;
    private final ReportAiSchedulerProperties schedulerProperties;

    public ReportTaskHandler(ObjectMapper objectMapper,
                             ReportTaskRepository tasks,
                             DocumentRepository documents,
                             ObjectIntegrityRepository objectIntegrity,
                             ObjectProvider<ObjectStorage> objectStorages,
                             List<DocumentParserPort> parsers,
                             ObjectProvider<ObjectStorageResolver> storageResolvers,
                             AgentPipelineExecutor pipelineExecutor,
                             ObjectProvider<LlmProvider> llmProviders,
                             ObjectProvider<ToolRegistry> toolRegistries,
                             ObjectProvider<WorkflowEngine> workflows,
                             WordReleaseService wordRelease,
                             IdGenerator ids,
                             ReportAiSchedulerProperties schedulerProperties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.objectIntegrity = Objects.requireNonNull(objectIntegrity, "objectIntegrity");
        this.objectStorages = Objects.requireNonNull(objectStorages, "objectStorages");
        this.parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers"));
        this.storageResolvers = Objects.requireNonNull(storageResolvers, "storageResolvers");
        this.pipelineExecutor = Objects.requireNonNull(pipelineExecutor, "pipelineExecutor");
        this.llmProviders = Objects.requireNonNull(llmProviders, "llmProviders");
        this.toolRegistries = Objects.requireNonNull(toolRegistries, "toolRegistries");
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.wordRelease = Objects.requireNonNull(wordRelease, "wordRelease");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties");
    }

    @Override
    public void handle(TaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        ReportTaskPayload payload = parsePayload(context);
        ReportTask task = requireTask(payload);
        TenantContext.set(payload.tenantId());
        WorkflowInstance workflow = null;
        try {
            context.progress(5, "report task accepted", Map.of("phase", "accepted"));
            workflow = ensureWorkflow(payload, task);

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.PARSING);
            context.progress(10, "parsing canonical document", Map.of("phase", "parsing"));
            Document document = loadDocument(payload);

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.PLANNING);
            context.progress(25, "planning report execution", Map.of("phase", "planning"));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("document", document);
            state.put("language", payload.language() == null ? "python" : payload.language());
            state.put("workspaceId", payload.workspaceId() == null ? payload.taskId() : payload.workspaceId());
            if (!payload.runtimeRequest().isEmpty()) {
                state.put("runtimeRequest", payload.runtimeRequest());
            }
            runAgents(context, payload, document, state, List.of(new PlannerAgent()));

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.EXECUTING);
            context.progress(45, "generating and executing code", Map.of("phase", "executing"));
            runAgents(context, payload, document, state, List.of(new CoderAgent(), new RuntimeAgent()));

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.REVIEWING);
            context.progress(72, "reviewing generated report", Map.of("phase", "reviewing"));
            runAgents(context, payload, document, state, List.of(new DocumentAgent(), new ReviewerAgent()));

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.EXPORTING);
            context.progress(90, "exporting review-gated DOCX", Map.of("phase", "exporting"));
            export(document, payload, context);

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.SUCCEEDED);
            completeWorkflow(workflow, payload.tenantId());
            context.progress(100, "report task completed", Map.of("phase", "completed"));
        } catch (RuntimeException failure) {
            if (context.attempt() >= schedulerProperties.getReportTaskMaxAttempts()) {
                failWorkflow(workflow, payload.tenantId());
                failTask(payload);
            }
            throw safeFailure(failure);
        } finally {
            TenantContext.clear();
        }
    }

    private ReportTaskPayload parsePayload(TaskExecutionContext context) {
        try {
            JsonNode root = objectMapper.readTree(context.payload());
            if (root == null || !root.isObject() || root.size() > MAX_PAYLOAD_FIELDS) {
                throw new IllegalArgumentException("report task payload is invalid");
            }
            String taskId = text(root, "taskId", context.taskId());
            if (!context.taskId().equals(taskId)) {
                throw new IllegalArgumentException("report task payload identity mismatch");
            }
            TenantId tenant = new TenantId(requiredText(root, "tenantId"));
            String documentId = optionalText(root, "documentId");
            String objectKey = requiredText(root, "documentObjectKey");
            String mediaType = optionalText(root, "mediaType");
            long size = number(root, "size", -1);
            String sha256 = optionalText(root, "sha256");
            String workflow = optionalText(root, "workflowDefinitionId");
            String workspace = optionalText(root, "workspaceId");
            String language = optionalText(root, "language");
            Map<String, Object> runtimeRequest = root.has("runtimeRequest") && root.get("runtimeRequest").isObject()
                    ? objectMapper.convertValue(root.get("runtimeRequest"), new TypeReference<Map<String, Object>>() {
                    }) : Map.of();
            return new ReportTaskPayload(taskId, tenant, documentId, objectKey, mediaType, size, sha256,
                    workflow == null ? DEFAULT_WORKFLOW : workflow, workspace, language, runtimeRequest);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new ReportTaskFailure("report task payload is invalid", failure);
        }
    }

    private ReportTask requireTask(ReportTaskPayload payload) {
        ReportTask task = tasks.findById(payload.tenantId(), payload.taskId())
                .orElseThrow(() -> new ReportTaskFailure("report task does not exist", null));
        if (!task.documentObjectKey().equals(payload.documentObjectKey())) {
            throw new ReportTaskFailure("report task document does not match payload", null);
        }
        if (task.status() == ReportTaskStatus.CANCELLED || task.status() == ReportTaskStatus.FAILED
                || task.status() == ReportTaskStatus.SUCCEEDED) {
            throw new ReportTaskFailure("report task is not runnable", null);
        }
        return task;
    }

    private Document loadDocument(ReportTaskPayload payload) {
        String documentId = payload.documentId() == null ? inferDocumentId(payload.documentObjectKey()) : payload.documentId();
        if (documentId != null) {
            var existing = documents.findById(payload.tenantId(), documentId);
            if (existing.isPresent()) {
                Document document = existing.get();
                if (!payload.tenantId().equals(document.tenantId())
                        || !payload.documentObjectKey().equals(document.sourceObjectKey())) {
                    throw new ReportTaskFailure("stored document identity does not match task", null);
                }
                return document;
            }
        }

        ObjectStorageResolver resolver = storageResolvers.getIfAvailable();
        if (resolver == null) {
            throw new ReportTaskFailure("object storage is not configured", null);
        }
        StoredObject source = sourceReference(payload, documentId);
        String fileName = fileName(payload.documentObjectKey());
        DocumentParserPort parser = parsers.stream()
                .filter(candidate -> candidate.supports(source.mediaType(), fileName))
                .findFirst()
                .orElseThrow(() -> new ReportTaskFailure("document format is unsupported", null));
        try (InputStream input = resolver.open(payload.tenantId(), source)) {
            ParserResult parsed = parser.parse(new ParserRequest(input, fileName, source.objectKey(), payload.tenantId(),
                    source.mediaType(), source.size(), documentId));
            documents.save(parsed.document());
            return parsed.document();
        } catch (IOException | RuntimeException failure) {
            throw new ReportTaskFailure("document could not be parsed", failure);
        }
    }

    private StoredObject sourceReference(ReportTaskPayload payload, String documentId) {
        if (documentId != null) {
            List<ObjectIntegrity> records = objectIntegrity.findByOwner(payload.tenantId(), "DOCUMENT", documentId);
            for (ObjectIntegrity record : records) {
                if ("SOURCE".equals(record.role()) && payload.documentObjectKey().equals(record.object().objectKey())) {
                    return record.object();
                }
            }
        }
        if (payload.mediaType() == null || payload.sha256() == null || payload.size() <= 0) {
            throw new ReportTaskFailure("document integrity metadata is missing", null);
        }
        return new StoredObject(payload.documentObjectKey(), payload.mediaType(), payload.size(),
                payload.sha256().toLowerCase(Locale.ROOT));
    }

    private void runAgents(TaskExecutionContext context, ReportTaskPayload payload, Document document,
                           Map<String, Object> state, List<? extends Agent> agents) {
        String pipelineId = payload.taskId() + "-" + agents.stream().map(Agent::type)
                .reduce((left, right) -> left + "-" + right).orElse("pipeline");
        AgentContext agentContext = new AgentContext(payload.taskId(), payload.tenantId(), state,
                llmProviders.getIfAvailable(), toolRegistries.getIfAvailable());
        AgentPipelineResult pipeline;
        do {
            if (state.get("runtimeRequest") == null && state.get("code") != null
                    && agents.stream().anyMatch(agent -> RuntimeAgent.TYPE.equals(agent.type()))) {
                state.put("runtimeRequest", Map.of("entryPoint", "main.py", "source",
                        bound(String.valueOf(state.get("code")), 128 * 1024)));
            }
            pipeline = pipelineExecutor.run(pipelineId, agentContext, agents);
            state.putAll(pipeline.checkpoint().state());
            // JSONB persistence restores the canonical document as a map. Reattach
            // the validated domain object before invoking ReviewerAgent or exporting.
            state.put("document", document);
            if (pipeline.status() == AgentPipelineStatus.WAITING) {
                throw new ReportTaskFailure("agent pipeline is waiting for external input", null);
            }
            if (pipeline.status() == AgentPipelineStatus.FAILED) {
                throw new ReportTaskFailure(bound(pipeline.agentResult().message(), 512), null);
            }
            context.progress(Math.min(85, 30 + state.size()), "agent stage completed",
                    Map.of("agents", agents.stream().map(Agent::type)
                            .reduce((left, right) -> left + "," + right).orElse("")));
        } while (pipeline.status() == AgentPipelineStatus.RUNNING);
    }

    private void export(Document document, ReportTaskPayload payload, TaskExecutionContext context) {
        BoundedOutputStream output = new BoundedOutputStream(MAX_EXPORT_BYTES);
        AssetResolver resolver = assetResolver(document, payload);
        ReleaseResult result = wordRelease.release(document, output,
                new WordBuildOptions(WordBuildOptions.defaults().maxAssetBytes(),
                        WordBuildOptions.defaults().maxImageWidth(), WordBuildOptions.defaults().maxImageHeight(),
                        WordBuildOptions.defaults().allowedImageMediaTypes(), resolver));
        if (!result.released()) {
            throw new ReportTaskFailure("document review rejected export", null);
        }
        ObjectStorage storage = objectStorages.getIfAvailable();
        if (storage == null) {
            throw new ReportTaskFailure("object storage is not configured for export", null);
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length == 0) {
            throw new ReportTaskFailure("DOCX export is empty", null);
        }
        String objectKey = "documents/" + tenantPrefix(payload.tenantId().value())
                + "/tasks/" + payload.taskId() + "/output.docx";
        String checksum = sha256(bytes);
        StoredObject outputReference = new StoredObject(objectKey,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes.length, checksum);
        storage.put(outputReference, new java.io.ByteArrayInputStream(bytes));
        objectIntegrity.save(new ObjectIntegrity(ids.nextId(), payload.tenantId(), "TASK", payload.taskId(),
                "OUTPUT", outputReference, java.time.Instant.now()));
        context.progress(95, "DOCX export generated", Map.of("bytes", String.valueOf(output.size())));
    }

    private AssetResolver assetResolver(Document document, ReportTaskPayload payload) {
        ObjectStorageResolver resolver = storageResolvers.getIfAvailable();
        if (resolver == null || document.assets().isEmpty()) {
            return AssetResolver.none();
        }
        return key -> {
            DocumentAsset asset = document.assets().stream()
                    .filter(candidate -> candidate.objectKey().equals(key))
                    .findFirst().orElse(null);
            if (asset == null) {
                return java.util.Optional.empty();
            }
            StoredObject reference = new StoredObject(asset.objectKey(), asset.mediaType(), asset.size(), asset.sha256());
            return java.util.Optional.of(new AssetResolver.Asset(asset.mediaType(),
                    resolver.open(payload.tenantId(), reference), asset.size()));
        };
    }

    private WorkflowInstance ensureWorkflow(ReportTaskPayload payload, ReportTask task) {
        WorkflowEngine engine = workflows.getIfAvailable();
        if (engine == null) {
            return null;
        }
        java.util.Optional<WorkflowInstance> existing = engine.findByTaskId(payload.taskId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (task.status() != ReportTaskStatus.CREATED) {
            return null;
        }
        try {
            return engine.start(payload.workflowDefinitionId(), payload.taskId());
        } catch (BusinessException missingDefinition) {
            // Workflow definitions are tenant data and may be provisioned after the
            // first deployment.  The report task remains executable without one.
            if (missingDefinition.getErrorCode() == com.reportai.common.api.ErrorCode.NOT_FOUND) {
                return null;
            }
            if (missingDefinition.getErrorCode() == com.reportai.common.api.ErrorCode.CONFLICT) {
                return engine.findByTaskId(payload.taskId()).orElseThrow(() -> missingDefinition);
            }
            throw missingDefinition;
        }
    }

    private void completeWorkflow(WorkflowInstance workflow, TenantId tenantId) {
        if (workflow == null) {
            return;
        }
        try {
            TenantContext.set(tenantId);
            workflows.getIfAvailable().complete(workflow.id(), workflow.version());
        } catch (RuntimeException ignored) {
            // Task completion remains authoritative; workflow reconciliation can repair
            // a lost final transition without exposing internal details to the client.
        } finally {
            TenantContext.clear();
        }
    }

    private void failWorkflow(WorkflowInstance workflow, TenantId tenantId) {
        if (workflow == null) {
            return;
        }
        try {
            TenantContext.set(tenantId);
            workflows.getIfAvailable().fail(workflow.id(), workflow.version());
        } catch (RuntimeException ignored) {
            // Best effort; the scheduler's durable failure is the source of truth.
        } finally {
            TenantContext.clear();
        }
    }

    private void failTask(ReportTaskPayload payload) {
        try {
            for (int attempt = 0; attempt < MAX_TRANSITION_RETRIES; attempt++) {
                ReportTask current = tasks.findById(payload.tenantId(), payload.taskId()).orElse(null);
                if (current == null || current.status() == ReportTaskStatus.FAILED
                        || current.status() == ReportTaskStatus.CANCELLED
                        || current.status() == ReportTaskStatus.SUCCEEDED) {
                    return;
                }
                ReportTask next = current.transitionTo(ReportTaskStatus.FAILED);
                if (tasks.update(next, current.version())) {
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Preserve the scheduler failure; an aggregate reconciliation job can repair it.
        }
    }

    private ReportTask advance(TenantId tenantId, String taskId, ReportTaskStatus target) {
        for (int attempt = 0; attempt < MAX_TRANSITION_RETRIES; attempt++) {
            ReportTask current = tasks.findById(tenantId, taskId)
                    .orElseThrow(() -> new ReportTaskFailure("report task does not exist", null));
            if (current.status() == target || rank(current.status()) > rank(target)) {
                return current;
            }
            if (current.status() == ReportTaskStatus.CANCELLED || current.status() == ReportTaskStatus.FAILED
                    || current.status() == ReportTaskStatus.SUCCEEDED) {
                throw new ReportTaskFailure("report task is not runnable", null);
            }
            ReportTask next = current.transitionTo(target);
            if (tasks.update(next, current.version())) {
                return next;
            }
        }
        throw new ReportTaskFailure("report task version conflict", null);
    }

    private static int rank(ReportTaskStatus status) {
        return switch (status) {
            case CREATED -> 0;
            case PARSING -> 1;
            case PLANNING -> 2;
            case EXECUTING -> 3;
            case REVIEWING -> 4;
            case EXPORTING -> 5;
            case SUCCEEDED -> 6;
            case PAUSED, FAILED, CANCELLED -> -1;
        };
    }

    private static String inferDocumentId(String objectKey) {
        String file = fileName(objectKey);
        if (!file.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return null;
        }
        String value = file.substring(0, file.length() - 5);
        return value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") ? value : null;
    }

    private static String fileName(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash < 0 ? objectKey : objectKey.substring(slash + 1);
    }

    private static String text(JsonNode root, String field, String fallback) {
        String value = optionalText(root, field);
        return value == null ? fallback : value;
    }

    private static String requiredText(JsonNode root, String field) {
        String value = optionalText(root, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 2048) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.textValue().strip();
    }

    private static long number(JsonNode root, String field, long fallback) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.longValue();
    }

    private static String bound(String value, int max) {
        if (value == null || value.isBlank()) {
            return "agent execution failed";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String tenantPrefix(String tenantId) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(tenantId.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static RuntimeException safeFailure(RuntimeException failure) {
        if (failure instanceof ReportTaskFailure) {
            return failure;
        }
        return new ReportTaskFailure("report task execution failed", failure);
    }

    private static final class ReportTaskFailure extends RuntimeException {
        private ReportTaskFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class BoundedOutputStream extends ByteArrayOutputStream {
        private BoundedOutputStream(int limit) {
            super(Math.min(limit, 64 * 1024));
            this.limit = limit;
        }

        private final int limit;

        @Override
        public synchronized void write(int value) {
            ensureCapacity(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            ensureCapacity(length);
            super.write(bytes, offset, length);
        }

        private void ensureCapacity(int additional) {
            if (additional < 0 || size() > limit - additional) {
                throw new ReportTaskFailure("DOCX export exceeds the maximum size", null);
            }
        }
    }
}
