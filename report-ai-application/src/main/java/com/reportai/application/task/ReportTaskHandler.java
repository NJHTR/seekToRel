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
import com.reportai.agent.CleanupAgent;
import com.reportai.agent.DocumentAgent;
import com.reportai.agent.ExecutionAgent;
import com.reportai.agent.InstallerAgent;
import com.reportai.agent.PlannerAgent;
import com.reportai.agent.ReviewerAgent;
import com.reportai.agent.RuntimeAgent;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.config.properties.ReportAiLlmProperties;
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.contract.AgentId;
import com.reportai.common.contract.CanonicalJson;
import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.contract.SnapshotId;
import com.reportai.common.contract.SnapshotRef;
import com.reportai.common.contract.TaskId;
import com.reportai.common.time.TimeSource;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentAsset;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.ChatAttachmentResolver;
import com.reportai.llm.ChatContentPart;
import com.reportai.parser.DocumentParserPort;
import com.reportai.parser.ParserRequest;
import com.reportai.parser.ParserResult;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.EnvironmentManifest;
import com.reportai.runtime.EnvironmentManifestValidator;
import com.reportai.runtime.InputRequirement;
import com.reportai.runtime.EnvironmentResolver;
import com.reportai.runtime.PythonDependencyRequest;
import com.reportai.runtime.PythonImportDependencyResolver;
import com.reportai.scheduler.TaskExecutionContext;
import com.reportai.scheduler.TaskHandler;
import com.reportai.scheduler.NonRetryableTaskException;
import com.reportai.storage.DocumentRepository;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.ObjectStorageResolver;
import com.reportai.storage.ArtifactStorageResolver;
import com.reportai.storage.StoredObject;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskPayload;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import com.reportai.tool.ToolRegistry;
import com.reportai.word.AssetResolver;
import com.reportai.word.ReleaseResult;
import com.reportai.word.WordBuildOptions;
import com.reportai.word.TemplatePatch;
import com.reportai.word.WordReleaseService;
import com.reportai.workflow.WorkflowEngine;
import com.reportai.workflow.WorkflowInstance;
import com.reportai.review.precheck.ContextBudget;
import com.reportai.review.precheck.PrecheckDecision;
import com.reportai.review.precheck.PrecheckRequest;
import com.reportai.review.precheck.PrecheckResult;
import com.reportai.review.precheck.ProgramPrecheckEngine;
import com.reportai.review.precheck.RuleDescriptor;
import com.reportai.review.precheck.RulePack;
import com.reportai.review.precheck.PrecheckStage;
import com.reportai.review.precheck.PrecheckSeverity;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportTaskHandler.class);
    private static final String DEFAULT_WORKFLOW = "report";
    private static final Instant PRECHECK_POLICY_EXPIRES_AT = Instant.parse("2099-12-31T23:59:59Z");
    private static final int MAX_PAYLOAD_FIELDS = 32;
    private static final int MAX_TRANSITION_RETRIES = 4;
    private static final int MAX_EXPORT_BYTES = 50 * 1024 * 1024;
    /** Embedded DOCX images are mounted under a deterministic, read-only input namespace. */
    private static final int MAX_DOCUMENT_ASSETS = 128;
    private static final long MAX_DOCUMENT_ASSET_BYTES = 512L * 1024 * 1024;
    private static final long MAX_DOCUMENT_ASSET_TOTAL_BYTES = 1024L * 1024 * 1024;
    /** Stable, non-user-controlled path for the immutable source report. */
    static final String SOURCE_DOCUMENT_INPUT_PATH = "source-document.docx";

    private final ObjectMapper objectMapper;
    private final ReportTaskRepository tasks;
    private final DocumentRepository documents;
    private final ObjectIntegrityRepository objectIntegrity;
    private final ObjectProvider<ObjectStorage> objectStorages;
    private final List<DocumentParserPort> parsers;
    private final ObjectProvider<ObjectStorageResolver> storageResolvers;
    private final ObjectProvider<ArtifactStorageResolver> artifactStorageResolvers;
    private final AgentPipelineExecutor pipelineExecutor;
    private final ObjectProvider<LlmProvider> llmProviders;
    private final ObjectProvider<ToolRegistry> toolRegistries;
    private final ObjectProvider<WorkflowEngine> workflows;
    private final WordReleaseService wordRelease;
    private final IdGenerator ids;
    private final ReportAiSchedulerProperties schedulerProperties;
    private final ReportAiLlmProperties llmProperties;
    private final ReportAiRuntimeProperties runtimeProperties;
    private final ObjectProvider<EnvironmentResolver> environmentResolvers;
    private final ObjectProvider<ExecutionArtifactPersistenceService> artifactPersistence;
    private final ObjectProvider<TaskInputApplicationService> taskInputs;
    private final EnvironmentManifestValidator environmentManifestValidator = new EnvironmentManifestValidator();
    private final PythonImportDependencyResolver pythonImportDependencyResolver = new PythonImportDependencyResolver();
    private final TimeSource precheckTimeSource = TimeSource.systemUtc();
    private final ProgramPrecheckEngine precheckEngine = new ProgramPrecheckEngine(precheckTimeSource);
    private final RulePack precheckRulePack = RulePack.of("application-default", "1", "policy-1",
            List.of(new RuleDescriptor("input.policy", PrecheckStage.FORMAT_SECURITY,
                    PrecheckSeverity.CRITICAL, "application policy", 90)));
    private final ScheduledExecutorService agentProgressHeartbeats =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "report-ai-agent-progress");
                thread.setDaemon(true);
                return thread;
            });

    public ReportTaskHandler(ObjectMapper objectMapper,
                             ReportTaskRepository tasks,
                             DocumentRepository documents,
                             ObjectIntegrityRepository objectIntegrity,
                             ObjectProvider<ObjectStorage> objectStorages,
                             List<DocumentParserPort> parsers,
                             ObjectProvider<ObjectStorageResolver> storageResolvers,
                             ObjectProvider<ArtifactStorageResolver> artifactStorageResolvers,
                             AgentPipelineExecutor pipelineExecutor,
                             ObjectProvider<LlmProvider> llmProviders,
                             ObjectProvider<ToolRegistry> toolRegistries,
                             ObjectProvider<WorkflowEngine> workflows,
                             WordReleaseService wordRelease,
                             IdGenerator ids,
                             ReportAiSchedulerProperties schedulerProperties,
                             ReportAiLlmProperties llmProperties,
                             ReportAiRuntimeProperties runtimeProperties,
                             ObjectProvider<EnvironmentResolver> environmentResolvers,
                             ObjectProvider<ExecutionArtifactPersistenceService> artifactPersistence,
                             ObjectProvider<TaskInputApplicationService> taskInputs) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.objectIntegrity = Objects.requireNonNull(objectIntegrity, "objectIntegrity");
        this.objectStorages = Objects.requireNonNull(objectStorages, "objectStorages");
        this.parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers"));
        this.storageResolvers = Objects.requireNonNull(storageResolvers, "storageResolvers");
        this.artifactStorageResolvers = Objects.requireNonNull(artifactStorageResolvers, "artifactStorageResolvers");
        this.pipelineExecutor = Objects.requireNonNull(pipelineExecutor, "pipelineExecutor");
        this.llmProviders = Objects.requireNonNull(llmProviders, "llmProviders");
        this.toolRegistries = Objects.requireNonNull(toolRegistries, "toolRegistries");
        this.workflows = Objects.requireNonNull(workflows, "workflows");
        this.wordRelease = Objects.requireNonNull(wordRelease, "wordRelease");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties");
        this.llmProperties = Objects.requireNonNull(llmProperties, "llmProperties");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties");
        this.environmentResolvers = Objects.requireNonNull(environmentResolvers, "environmentResolvers");
        this.artifactPersistence = Objects.requireNonNull(artifactPersistence, "artifactPersistence");
        this.taskInputs = Objects.requireNonNull(taskInputs, "taskInputs");
    }

    @Override
    public void handle(TaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        ReportTaskPayload payload = parsePayload(context);
        ReportTask task = requireTask(payload);
        TenantContext.set(payload.tenantId());
        WorkflowInstance workflow = null;
        String stage = "ACCEPTED";
        try {
            LOGGER.info("report task accepted taskId={} tenantId={} attempt={}", payload.taskId(),
                    payload.tenantId().value(), context.attempt());
            context.progress(5, "report task accepted", Map.of("phase", "accepted"));
            workflow = ensureWorkflow(payload, task);

            stage = "PARSING";
            LOGGER.info("report task stage taskId={} stage={}", payload.taskId(), stage);
            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.PARSING);
            context.progress(10, "parsing canonical document", Map.of("phase", "parsing"));
            Document document = loadDocument(payload);

            stage = "PLANNING";
            LOGGER.info("report task stage taskId={} stage={}", payload.taskId(), stage);
            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.PLANNING);
            context.progress(25, "planning report execution", Map.of("phase", "planning"));
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("document", document);
            state.put("language", payload.language() == null ? "python" : payload.language());
            state.put("workspaceId", payload.workspaceId() == null ? payload.taskId() : payload.workspaceId());
            state.put("runtimeCapabilities", runtimeProperties.getCapabilities());
            if (!payload.runtimeRequest().isEmpty()) {
                state.put("runtimeRequest", payload.runtimeRequest());
            }
            materializeTaskInputs(state, payload);
            // DOCX-embedded images are immutable document inputs too.  Mount
            // them before planning so both the multimodal provider and a
            // sandbox OCR fallback see the exact bytes that were parsed and
            // integrity-checked at upload time.
            materializeDocumentAssets(state, payload, document);
            materializeSourceDocument(state, payload, document);
            runAgents(context, payload, document, state, List.of(new PlannerAgent()));

            stage = "EXECUTING";
            LOGGER.info("report task stage taskId={} stage={}", payload.taskId(), stage);
            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.EXECUTING);
            context.progress(45, "generating and executing code", Map.of("phase", "executing"));
            materializeManifestIfPresent(state, payload);
            List<Agent> executionAgents = new ArrayList<>();
            executionAgents.add(new CoderAgent());
            String workspaceId = String.valueOf(state.get("workspaceId"));
            if (!workspaceId.matches("[A-Za-z0-9_-]{1,128}")) {
                throw new NonRetryableTaskException("ENVIRONMENT_UNAVAILABLE: workspace id is invalid");
            }
            executionAgents.add(new EnvironmentPreflightAgent(environmentManifestValidator,
                    Path.of(runtimeProperties.getWorkspaceRoot()).toAbsolutePath().normalize(),
                    workspaceId, this::manifest));
            executionAgents.add(new InstallerAgent());
            executionAgents.add(new ExecutionAgent());
            executionAgents.add(new ExecutionArtifactAgent(artifactPersistence.getIfAvailable()));
            executionAgents.add(new CleanupAgent());
            runAgents(context, payload, document, state, executionAgents);
            lockTaskManifestDigest(payload, state);

            stage = "REVIEWING";
            LOGGER.info("report task stage taskId={} stage={}", payload.taskId(), stage);
            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.REVIEWING);
            context.progress(72, "reviewing generated report", Map.of("phase", "reviewing"));
            // Generate explanatory draft first. The draft is not yet the release
            // document: deterministic assembly below combines only verified runtime
            // state and persisted artifact references with the canonical source.
            runAgents(context, payload, document, state, List.of(new DocumentAgent()));
            document = new ReportDocumentAssemblyService().assemble(document, state);
            state.put("document", document);
            runAgents(context, payload, document, state, List.of(new ReviewerAgent()));

            stage = "EXPORTING";
            LOGGER.info("report task stage taskId={} stage={}", payload.taskId(), stage);
            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.EXPORTING);
            context.progress(90, "exporting review-gated DOCX", Map.of("phase", "exporting"));
            export(document, payload, context, state);

            advance(payload.tenantId(), payload.taskId(), ReportTaskStatus.SUCCEEDED);
            completeWorkflow(workflow, payload.tenantId());
            context.progress(100, "report task completed", Map.of("phase", "completed"));
        } catch (RuntimeException failure) {
            LOGGER.error("report task failed taskId={} stage={} attempt={} error={}", payload.taskId(), stage,
                    context.attempt(), bound(failure.getMessage(), 512));
            if (failure instanceof NonRetryableTaskException
                    || context.attempt() >= schedulerProperties.getReportTaskMaxAttempts()) {
                failWorkflow(workflow, payload.tenantId());
                failTask(payload);
            }
            throw safeFailure(failure);
        } finally {
            TenantContext.clear();
        }
    }

    /** Copies tenant-owned immutable input objects before any generated code can run. */
    private void materializeTaskInputs(Map<String, Object> state, ReportTaskPayload payload) {
        TaskInputApplicationService service = taskInputs.getIfAvailable();
        if (service == null) {
            return;
        }
        String workspaceId = String.valueOf(state.get("workspaceId"));
        try {
            TaskInputApplicationService.Materialization materialization = service.materialize(
                    payload.tenantId(), payload.taskId(), workspaceId,
                    Path.of(runtimeProperties.getWorkspaceRoot()).toAbsolutePath().normalize());
            state.putAll(materialization.state());
        } catch (RuntimeException failure) {
            throw new NonRetryableTaskException("PYTHON_INPUT_MATERIALIZATION_FAILED: "
                    + bound(failure.getMessage(), 256));
        }
    }

    /**
     * Copies embedded document assets to a task-scoped input directory.  The
     * object-store resolver is the only source of bytes; client paths and DOCX
     * relationship names never cross this boundary.  Every copy is checked for
     * the parser-declared size and SHA-256 before an atomic move into the
     * workspace, so retries cannot observe a partial or stale image.
     */
    private void materializeDocumentAssets(Map<String, Object> state, ReportTaskPayload payload,
                                           Document document) {
        if (document == null || document.assets().isEmpty()) {
            return;
        }
        if (document.assets().size() > MAX_DOCUMENT_ASSETS) {
            throw new NonRetryableTaskException("DOCUMENT_ASSET_LIMIT: too many embedded document assets");
        }
        ObjectStorageResolver resolver = storageResolvers.getIfAvailable();
        if (resolver == null) {
            throw new NonRetryableTaskException("DOCUMENT_ASSET_STORAGE_UNAVAILABLE: embedded assets cannot be read");
        }
        String workspaceId = String.valueOf(state.get("workspaceId"));
        if (!workspaceId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new NonRetryableTaskException("ENVIRONMENT_UNAVAILABLE: workspace id is invalid");
        }
        Path workspaceRoot = Path.of(runtimeProperties.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = workspaceRoot.resolve(workspaceId).normalize();
        Path assetRoot = workspace.resolve("input").resolve("document-assets").normalize();
        if (!workspace.startsWith(workspaceRoot) || !assetRoot.startsWith(workspace)
                || Files.isSymbolicLink(workspace) || Files.isSymbolicLink(assetRoot)) {
            throw new NonRetryableTaskException("DOCUMENT_ASSET_PATH_INVALID: workspace path is unsafe");
        }
        long total = 0;
        List<Map<String, Object>> metadata = new ArrayList<>();
        try {
            Files.createDirectories(workspaceRoot);
            if (!Files.exists(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(workspaceRoot)) {
                throw new IOException("workspace root is invalid");
            }
            if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(workspace)) {
                throw new IOException("workspace is symbolic link");
            }
            Files.createDirectories(workspace);
            if (Files.isSymbolicLink(workspace)) throw new IOException("workspace is symbolic link");
            Path inputRoot = workspace.resolve("input").normalize();
            if (!inputRoot.startsWith(workspace) || Files.isSymbolicLink(inputRoot)) {
                throw new IOException("input directory is invalid");
            }
            if (Files.exists(inputRoot, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(inputRoot)) {
                throw new IOException("input directory is symbolic link");
            }
            Files.createDirectories(inputRoot);
            clearMaterializedDirectory(assetRoot);
            Files.createDirectories(assetRoot);
            int ordinal = 0;
            for (DocumentAsset asset : document.assets()) {
                if (asset.size() <= 0 || asset.size() > MAX_DOCUMENT_ASSET_BYTES) {
                    throw new IOException("embedded asset exceeds permitted size");
                }
                total = Math.addExact(total, asset.size());
                if (total > MAX_DOCUMENT_ASSET_TOTAL_BYTES) {
                    throw new IOException("embedded assets exceed aggregate size limit");
                }
                String fileName = materializedAssetFileName(++ordinal, asset);
                Path target = assetRoot.resolve(fileName).normalize();
                if (!target.startsWith(assetRoot) || Files.isSymbolicLink(target)) {
                    throw new IOException("embedded asset path is unsafe");
                }
                Path temp = Files.createTempFile(assetRoot, ".asset-", ".tmp");
                try {
                    StoredObject reference = new StoredObject(asset.objectKey(), asset.mediaType(),
                            asset.size(), asset.sha256());
                    try (InputStream input = resolver.open(payload.tenantId(), reference)) {
                        copyVerified(input, temp, asset.size(), asset.sha256());
                    }
                    moveAtomically(temp, target);
                } catch (IOException | RuntimeException failure) {
                    Files.deleteIfExists(temp);
                    throw failure;
                }
                String relative = "document-assets/" + fileName;
                metadata.add(Map.of("relativePath", relative, "originalFileName", asset.originalFileName(),
                        "mediaType", asset.mediaType(), "size", asset.size(), "sha256", asset.sha256(),
                        "isImage", asset.isImage()));
            }
        } catch (IOException | ArithmeticException failure) {
            throw new NonRetryableTaskException("DOCUMENT_ASSET_MATERIALIZATION_FAILED: "
                    + bound(failure.getMessage(), 256));
        }
        state.put("documentAssets", List.copyOf(metadata));
        // Bind embedded assets into preflight just like explicit task inputs.
        // Keep this as metadata only; binary bytes never enter checkpoint JSON.
        state.put("documentAssetInputs", metadata.stream().map(value -> Map.of(
                "relativePath", value.get("relativePath"), "size", value.get("size"),
                "sha256", value.get("sha256"))).toList());
    }

    /**
     * Materializes the canonical source report itself as a read-only runtime
     * input.  The parser intentionally keeps only the normalized document model
     * in PostgreSQL; generated programs that need to inspect the original DOCX
     * must therefore receive the exact object-store bytes, not a client path or
     * a re-created approximation.  The fixed name is part of the execution
     * contract and is advertised to Planner/Coder as
     * {@code /workspace/input/source-document.docx}.
     */
    private void materializeSourceDocument(Map<String, Object> state, ReportTaskPayload payload,
                                           Document document) {
        if (document == null) {
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_MISSING: canonical document is absent");
        }
        ObjectStorageResolver resolver = storageResolvers.getIfAvailable();
        if (resolver == null) {
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_STORAGE_UNAVAILABLE: source DOCX cannot be read");
        }
        StoredObject source = sourceReference(payload, document.id());
        if (!document.sourceObjectKey().equals(source.objectKey())) {
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_IDENTITY_MISMATCH: source object is not canonical");
        }
        if (!document.mediaType().equalsIgnoreCase(source.mediaType())) {
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_MEDIA_TYPE_MISMATCH: source media type changed");
        }
        long maxBytes = runtimeProperties.getMaxInputBytes();
        if (maxBytes <= 0 || source.size() <= 0 || source.size() > maxBytes) {
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_LIMIT: source DOCX exceeds runtime input limit");
        }

        String workspaceId = String.valueOf(state.get("workspaceId"));
        if (!workspaceId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new NonRetryableTaskException("ENVIRONMENT_UNAVAILABLE: workspace id is invalid");
        }
        Path workspaceRoot = Path.of(runtimeProperties.getWorkspaceRoot()).toAbsolutePath().normalize();
        Path workspace = workspaceRoot.resolve(workspaceId).normalize();
        Path inputRoot = workspace.resolve("input").normalize();
        Path target = inputRoot.resolve(SOURCE_DOCUMENT_INPUT_PATH).normalize();
        if (!workspace.startsWith(workspaceRoot) || !inputRoot.startsWith(workspace)
                || !target.startsWith(inputRoot) || Files.isSymbolicLink(workspace)
                || Files.isSymbolicLink(inputRoot) || Files.isSymbolicLink(target)) {
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_PATH_INVALID: source input path is unsafe");
        }
        boolean reused = false;
        try {
            Files.createDirectories(workspaceRoot);
            if (!Files.exists(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(workspaceRoot)) {
                throw new IOException("workspace root is invalid");
            }
            Files.createDirectories(workspace);
            if (Files.isSymbolicLink(workspace)) throw new IOException("workspace is symbolic link");
            Files.createDirectories(inputRoot);
            if (Files.isSymbolicLink(inputRoot)) throw new IOException("input directory is symbolic link");
            // TaskInputApplicationService owns arbitrary user input paths.  A
            // reserved source path must never overwrite one of those files.  A
            // matching immutable file is safe to reuse when a retry resumes
            // after materialization but before the checkpoint is persisted.
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        || !verifyFile(target, source.size(), source.sha256())) {
                    throw new IOException("reserved source input path already exists");
                }
                reused = true;
            } else {
                Path temp = Files.createTempFile(inputRoot, ".source-document-", ".tmp");
                try {
                    try (InputStream input = resolver.open(payload.tenantId(), source)) {
                        copyVerified(input, temp, source.size(), source.sha256());
                    }
                    moveAtomically(temp, target);
                } catch (IOException | RuntimeException failure) {
                    Files.deleteIfExists(temp);
                    throw failure;
                }
            }
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof NonRetryableTaskException nonRetryable) throw nonRetryable;
            throw new NonRetryableTaskException("DOCUMENT_SOURCE_MATERIALIZATION_FAILED: "
                    + bound(failure.getMessage(), 256));
        }
        Map<String, Object> metadata = Map.of(
                "relativePath", SOURCE_DOCUMENT_INPUT_PATH,
                "mediaType", source.mediaType(),
                "size", source.size(),
                "sha256", source.sha256(),
                "objectKey", source.objectKey(),
                "documentId", document.id());
        state.put("sourceDocumentInput", metadata);
        state.put("sourceDocumentInputDigest", source.sha256());
        LOGGER.info("source document input materialized taskId={} path=/workspace/input/{} size={} sha256={} reused={}",
                payload.taskId(), SOURCE_DOCUMENT_INPUT_PATH, source.size(), source.sha256(), reused);
    }

    /** Streams an already materialized source without loading a large DOCX into memory. */
    private static boolean verifyFile(Path path, long expectedSize, String expectedSha256) throws IOException {
        if (Files.size(path) != expectedSize) return false;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedSha256);
    }

    /**
     * Resolves image bytes at the last responsible moment for Planner/Coder.
     * The resolver is attached to the ephemeral AgentContext and is therefore
     * never serialized into an agent checkpoint.  It deliberately uses the
     * same tenant-bound object resolver and integrity metadata as the sandbox
     * mount, but applies the smaller multimodal request limits.
     */
    private ChatAttachmentResolver documentAssetAttachmentResolver(Document document,
                                                                    ReportTaskPayload payload) {
        if (document == null || document.assets().isEmpty()) {
            return ChatAttachmentResolver.none();
        }
        return (agentType, state) -> {
            if (!PlannerAgent.TYPE.equals(agentType) && !CoderAgent.TYPE.equals(agentType)) {
                return List.of();
            }
            ObjectStorageResolver resolver = storageResolvers.getIfAvailable();
            if (resolver == null) return List.of();
            List<ChatContentPart> parts = new ArrayList<>();
            long total = 0;
            int maxAttachments = llmProperties.getVisionMaxAttachments();
            long maxAttachmentBytes = llmProperties.getVisionMaxAttachmentBytes();
            long maxTotalBytes = llmProperties.getVisionMaxAttachmentTotalBytes();
            if (maxAttachments <= 0 || maxAttachmentBytes <= 0 || maxTotalBytes <= 0) {
                throw new IllegalStateException("vision attachment limits are invalid");
            }
            for (DocumentAsset asset : document.assets()) {
                if (!asset.isImage() || parts.size() >= maxAttachments
                        || asset.size() > maxAttachmentBytes
                        || total > maxTotalBytes - asset.size()) {
                    continue;
                }
                StoredObject reference = new StoredObject(asset.objectKey(), asset.mediaType(),
                        asset.size(), asset.sha256());
                try (InputStream input = resolver.open(payload.tenantId(), reference)) {
                    byte[] bytes = readVerified(input, asset.size(), asset.sha256(),
                            maxAttachmentBytes);
                    parts.add(ChatContentPart.image(asset.mediaType(), bytes));
                    total += bytes.length;
                } catch (IOException | RuntimeException failure) {
                    throw new IllegalStateException("document image could not be loaded for vision", failure);
                }
            }
            return List.copyOf(parts);
        };
    }

    private static void copyVerified(InputStream input, Path target, long expectedSize,
                                     String expectedSha256) throws IOException {
        if (input == null) throw new IOException("asset resolver returned no stream");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        long total = 0;
        byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(target)) {
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read == 0) continue;
                total = Math.addExact(total, read);
                if (total > expectedSize) throw new IOException("asset size mismatch");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        } catch (ArithmeticException overflow) {
            throw new IOException("asset size overflow", overflow);
        }
        String actual = java.util.HexFormat.of().formatHex(digest.digest());
        if (total != expectedSize || !actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("asset integrity check failed");
        }
    }

    private static byte[] readVerified(InputStream input, long expectedSize, String expectedSha256,
                                       long maxBytes) throws IOException {
        if (input == null || expectedSize <= 0 || expectedSize > maxBytes) {
            throw new IOException("vision asset size is invalid");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream((int) Math.min(expectedSize, 64 * 1024));
        long total = 0;
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) >= 0;) {
            if (read == 0) continue;
            total = Math.addExact(total, read);
            if (total > maxBytes) throw new IOException("vision asset exceeds permitted size");
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        String actual = java.util.HexFormat.of().formatHex(digest.digest());
        if (total != expectedSize || !actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("vision asset integrity check failed");
        }
        return output.toByteArray();
    }

    private static String materializedAssetFileName(int ordinal, DocumentAsset asset) {
        String prefix = String.format(Locale.ROOT, "%03d-%s-", ordinal,
                asset.sha256().substring(0, 12));
        // A filesystem segment is capped at 255 characters on supported host
        // filesystems.  The original name is retained when possible and
        // deterministically truncated otherwise; the content hash remains the
        // authoritative identity.
        int available = Math.max(1, 255 - prefix.length());
        String original = asset.originalFileName();
        if (original.length() > available) {
            original = original.substring(0, available);
        }
        return prefix + original;
    }

    private static void clearMaterializedDirectory(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(directory)) throw new IOException("asset directory is symbolic link");
        try (var paths = Files.walk(directory)) {
            List<Path> all = paths.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path path : all) {
                if (Files.isSymbolicLink(path)) throw new IOException("symbolic link in asset directory");
                Files.deleteIfExists(path);
            }
        }
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
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

    private PrecheckBundle buildPrechecks(ReportTaskPayload payload, Document document,
                                          List<? extends Agent> agents) {
        StoredObject source = sourceReference(payload, document.id());
        String sourceDigest = source.sha256();
        String canonicalInput = CanonicalJson.encode(Map.of(
                "taskId", payload.taskId(), "tenantId", payload.tenantId().value(),
                "documentId", document.id(), "sourceObjectKey", source.objectKey(),
                "sourceDigest", sourceDigest, "mediaType", source.mediaType(), "size", source.size()));
        Sha256Digest inputDigest = Sha256Digest.ofText(canonicalInput);
        SnapshotRef sourceRef = new SnapshotRef("source", source.objectKey(), source.mediaType(), source.size(), new Sha256Digest(sourceDigest));
        Instant createdAt = precheckTimeSource.now();
        InputSnapshot snapshot = new InputSnapshot(1,
                new SnapshotId("snapshot-" + inputDigest.value().substring(0, 16)), payload.tenantId(),
                List.of(sourceRef), inputDigest, List.of(), createdAt,
                Map.of("source", "report-task"));
        Map<String, PrecheckResult> receipts = new LinkedHashMap<>();
        for (Agent agent : agents) {
            PrecheckRequest request = new PrecheckRequest(payload.tenantId(), new TaskId(payload.taskId()),
                    "agent-pipeline", new AgentId(agent.type()), snapshot, canonicalInput, "text/plain", "1.0",
                    List.of(), precheckRulePack, ContextBudget.defaults(), List.of(), PRECHECK_POLICY_EXPIRES_AT);
            PrecheckResult result = precheckEngine.check(request);
            if (result.decision() != PrecheckDecision.ACCEPT || !result.acceptedForAgent()) {
                throw new NonRetryableTaskException("program precheck blocked agent " + agent.type());
            }
            receipts.put(agent.type(), result);
        }
        if (receipts.isEmpty()) throw new NonRetryableTaskException("program precheck has no agent receipts");
        return new PrecheckBundle(snapshot, receipts.get(agents.get(0).type()), Map.copyOf(receipts));
    }

    private record PrecheckBundle(InputSnapshot snapshot, PrecheckResult primary,
                                  Map<String, PrecheckResult> byAgent) { }

    private void runAgents(TaskExecutionContext context, ReportTaskPayload payload, Document document,
                           Map<String, Object> state, List<? extends Agent> agents) {
        if (llmProviders.getIfAvailable() == null) {
            String reason = "LLM provider is not configured; set REPORT_AI_LLM_ENABLED=true and provide a valid provider key";
            LOGGER.error("agent stage blocked taskId={} reason={}", payload.taskId(), reason);
            context.progress(Math.min(85, 30 + state.size()), "agent provider unavailable",
                    Map.of("phase", "llm", "errorCode", "LLM_PROVIDER_UNAVAILABLE", "retryable", "false"));
            throw new NonRetryableTaskException(reason);
        }
        String agentNames = agents.stream().map(Agent::type).reduce((left, right) -> left + "," + right).orElse("pipeline");
        // Keep the historical pipeline id so persisted [coder,runtime]
        // checkpoints can be migrated to the expanded installer/execution
        // lifecycle by AgentPipelineExecutor without creating a second cursor.
        List<String> agentTypes = agents.stream().map(Agent::type).toList();
        String pipelineSuffix = agentTypes.equals(List.of("coder", "preflight", "runtime"))
                || agentTypes.contains(InstallerAgent.TYPE)
                || agentTypes.contains(ExecutionAgent.TYPE)
                || agentTypes.contains(CleanupAgent.TYPE)
                ? "coder-runtime" : agentTypes.stream().reduce((left, right) -> left + "-" + right)
                .orElse("pipeline");
        String pipelineId = payload.taskId() + "-" + pipelineSuffix;
        PrecheckBundle prechecks = buildPrechecks(payload, document, agents);
        Map<String, Object> llmOptions = Map.of(
                "coderReasoningEffort", llmProperties.getCoderReasoningEffort(),
                "coderFallbackReasoningEffort", llmProperties.getCoderFallbackReasoningEffort(),
                "coderFallbackEnabled", llmProperties.isCoderFallbackEnabled(),
                "coderMaxOutputTokens", llmProperties.getCoderMaxOutputTokens(),
                "coderTimeout", llmProperties.getCoderTimeout());
        AgentPipelineResult pipeline;
        ScheduledFuture<?> heartbeat = agentProgressHeartbeats.scheduleAtFixedRate(() -> {
            try {
                context.progress(45, "waiting for provider response",
                        Map.of("phase", "agents", "agents", agentNames, "heartbeat", "true"));
            } catch (RuntimeException ignored) {
                LOGGER.debug("agent progress heartbeat stopped taskId={}", payload.taskId());
            }
        }, 15, 15, TimeUnit.SECONDS);
        try {
            do {
                if (state.get("code") != null
                        && agents.stream().anyMatch(agent -> RuntimeAgent.TYPE.equals(agent.type())
                        || ExecutionAgent.TYPE.equals(agent.type()))) {
                    String source = bound(String.valueOf(state.get("code")), 128 * 1024);
                    List<String> detectedDependencies = pythonImportDependencyResolver.resolvePackageNames(source);
                    Map<String, Object> runtimeRequest = new LinkedHashMap<>();
                    if (state.get("runtimeRequest") instanceof Map<?, ?> existing) {
                        existing.forEach((key, value) -> {
                            if (key instanceof String name && !Set.of("source", "dependencies",
                                    "packages", "resolvedDependencies", "environmentManifest",
                                    "dependencyVolume").contains(name)) {
                                runtimeRequest.put(name, value);
                            }
                        });
                    }
                    runtimeRequest.putIfAbsent("entryPoint", "main.py");
                    // The generated source is the only source allowed to cross
                    // the Coder -> Runtime boundary; a client cannot smuggle a
                    // different program through runtimeRequest.
                    runtimeRequest.put("source", source);
                    List<String> requestedDependencies = new ArrayList<>(detectedDependencies);
                    if (state.get("runtimeRequest") instanceof Map<?, ?> existing) {
                        Object raw = existing.get("dependencies");
                        if (raw == null) raw = existing.get("packages");
                        if (raw instanceof List<?> values) {
                            for (Object value : values) {
                                if (value instanceof String coordinate && !requestedDependencies.contains(coordinate)) {
                                    requestedDependencies.add(coordinate);
                                }
                            }
                        }
                    }
                    if (!requestedDependencies.isEmpty()) {
                        runtimeRequest.put("dependencies", List.copyOf(requestedDependencies));
                    }
                    Object installation = state.get("installation");
                    if (installation instanceof Map<?, ?> values
                            && values.get("dependencyVolume") instanceof String volume
                            && !volume.isBlank()) {
                        runtimeRequest.put("dependencyVolume", volume);
                        state.put("cleanupRequest", Map.of("dependencyVolume", volume));
                    } else if (agents.stream().anyMatch(agent -> CleanupAgent.TYPE.equals(agent.type()))) {
                        state.put("cleanupRequest", Map.of());
                    }
                    state.put("runtimeRequest", Map.copyOf(runtimeRequest));
                }
                if (agents.stream().anyMatch(agent -> EnvironmentPreflightAgent.TYPE.equals(agent.type()))) {
                    materializeManifestIfPresent(state, payload);
                }
                if (agents.stream().anyMatch(agent -> CleanupAgent.TYPE.equals(agent.type()))) {
                    state.putIfAbsent("cleanupRequest", Map.of());
                }
                AgentContext agentContext = new AgentContext(payload.taskId(), payload.tenantId(), state,
                        llmProviders.getIfAvailable(), toolRegistries.getIfAvailable(), llmOptions,
                        documentAssetAttachmentResolver(document, payload), Set.of("document"),
                        prechecks.snapshot(), prechecks.primary(), prechecks.byAgent());
                LOGGER.info("agent stage started taskId={} agents={}", payload.taskId(), agentNames);
                context.progress(Math.min(85, 30 + state.size()), "running agent stage: " + agentNames,
                        Map.of("phase", "agents", "agents", agentNames,
                                "agent", agentTypes.get(0)));
                pipeline = pipelineExecutor.run(pipelineId, agentContext, agents);
                state.putAll(pipeline.checkpoint().state());
                // JSONB persistence restores the canonical document as a map. Reattach
                // the validated domain object before invoking ReviewerAgent or exporting.
                state.put("document", document);
                if (pipeline.status() == AgentPipelineStatus.WAITING) {
                    throw new ReportTaskFailure("agent pipeline is waiting for external input", null);
                }
                if (pipeline.status() == AgentPipelineStatus.FAILED) {
                    String message = bound(pipeline.agentResult().message(), 512);
                    Object toolErrorCode = pipeline.agentResult().outputs().get("toolErrorCode");
                    message = failureMessage(message, toolErrorCode, pipeline.agentResult().outputs());
                    if (agents.stream().anyMatch(agent -> ReviewerAgent.TYPE.equals(agent.type()))) {
                        // Review is deterministic over the assembled canonical
                        // document. Retrying it cannot change the evidence and
                        // only replays expensive upstream LLM calls; expose the
                        // bounded findings and fail closed instead.
                        throw new NonRetryableTaskException(
                                reviewFailureMessage(message, pipeline.agentResult().outputs()));
                    }
                    if (message.startsWith("ENV_")
                            || message.startsWith("ARTIFACT_")
                            || message.contains("LLM_REQUEST_INVALID")
                            || (toolErrorCode != null && Set.of("PYTHON_DEPENDENCY_MISSING",
                            "PYTHON_INPUT_MISSING", "PYTHON_ARGUMENT_ERROR", "PYTHON_REQUEST_REJECTED")
                            .contains(String.valueOf(toolErrorCode)))
                            || (message.matches(".*LLM_PROVIDER_HTTP_4\\d\\d.*")
                            && !message.contains("LLM_PROVIDER_HTTP_408")
                            && !message.contains("LLM_PROVIDER_HTTP_429"))) {
                        throw new NonRetryableTaskException(message);
                    }
                    throw new ReportTaskFailure(message, null);
                }
                context.progress(Math.min(85, 30 + state.size()), "agent stage completed",
                        Map.of("phase", "agents", "agents", agentNames,
                                "agent", activeAgent(pipeline, agentTypes)));
                LOGGER.info("agent stage completed taskId={} agents={} status={}", payload.taskId(), agentNames,
                        pipeline.status());
            } while (pipeline.status() == AgentPipelineStatus.RUNNING);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private static String activeAgent(AgentPipelineResult pipeline, List<String> agentTypes) {
        if (pipeline == null || agentTypes.isEmpty()) return "pipeline";
        Object failed = pipeline.checkpoint().state().get("failedAgent");
        if (failed instanceof String value && !value.isBlank()) return value;
        int next = pipeline.checkpoint().nextAgentIndex();
        if (next >= 0 && next < agentTypes.size()) return agentTypes.get(next);
        return agentTypes.get(agentTypes.size() - 1);
    }

    /**
     * Materializes the execution environment before Coder runs.  When the signed
     * administrator catalog is enabled, it is the only authority allowed to
     * select an image or dependency hash.  A client/planner supplied manifest is
     * treated as an intent and is re-resolved against that catalog.
     */
    private void materializeManifestIfPresent(Map<String, Object> state, ReportTaskPayload payload) {
        Object raw = state.get("environmentManifest");
        Object runtimeRequest = state.get("runtimeRequest");
        if (raw == null && runtimeRequest instanceof Map<?, ?> request) {
            raw = request.get("environmentManifest");
        }
        // Do not lock an empty catalog result before Coder has produced the
        // source.  The generated imports are the deterministic dependency
        // intent; resolving an empty manifest here would create a provisional
        // manifestDigest which then changes as soon as Coder requests (for
        // example) torch or matplotlib.  A changed digest is deliberately
        // rejected after a manifest has been locked, so leave this state
        // absent until the Coder -> preflight boundary.  If a caller supplied
        // a runtime request, including an explicit empty one, it is already an
        // execution intent and can be resolved normally.
        if (raw == null && runtimeRequest == null) {
            return;
        }
        EnvironmentResolver resolver = environmentResolvers.getIfAvailable();
        if (resolver != null) {
            materializeFromCatalog(state, payload, raw, runtimeRequest, resolver);
            return;
        }
        if (runtimeProperties.isEnabled()) {
            // Defense in depth for an application context assembled outside the
            // normal startup validator.  A sandbox task must never proceed in
            // a legacy, unsigned mode and discover its missing digest only
            // after code has executed.
            throw new NonRetryableTaskException(
                    "ENV_CATALOG_NOT_CONFIGURED: signed environment catalog is required when runtime execution is enabled");
        }
        if (raw == null) return;
        EnvironmentManifest resolved;
        try {
            resolved = raw instanceof EnvironmentManifest value ? value : manifest(raw);
        } catch (RuntimeException invalid) {
            throw new NonRetryableTaskException("ENV_MANIFEST_INVALID: environment manifest is invalid");
        }
        Object previousDigest = state.get("environmentManifestDigest");
        if (previousDigest != null && !resolved.manifestDigest().equals(String.valueOf(previousDigest))) {
            throw new NonRetryableTaskException("ENV_MANIFEST_CHANGED: environment manifest changed during retry");
        }
        state.put("environmentManifest", resolved);
        state.put("environmentManifestDigest", resolved.manifestDigest());
        if (runtimeRequest instanceof Map<?, ?> request) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            request.forEach((key, value) -> {
                if (key instanceof String name && !"environmentManifest".equals(name)) {
                    sanitized.put(name, value);
                }
            });
            sanitized.put("resolvedDependencies", resolved.dependencies().stream()
                    .map(value -> Map.of("name", value.name(), "version", value.version(),
                            "sha256", String.valueOf(value.sha256())))
                    .toList());
            state.put("runtimeRequest", sanitized);
        }
    }

    private void materializeFromCatalog(Map<String, Object> state, ReportTaskPayload payload,
                                        Object raw, Object runtimeRequest, EnvironmentResolver resolver) {
        EnvironmentManifest requested = null;
        try {
            if (raw != null) {
                requested = raw instanceof EnvironmentManifest value ? value : manifest(raw);
            }
            String language = requested == null
                    ? String.valueOf(state.getOrDefault("language", payload.language() == null ? "python" : payload.language()))
                    : requested.language();
            String entryPoint = requested == null ? runtimeEntryPoint(runtimeRequest) : requested.entryPoint();
            // The task input service has already materialized tenant-owned files
            // under workspace/input.  When the planner does not provide an
            // explicit manifest input declaration, bind those immutable records
            // into the catalog manifest so scientific preflight can verify the
            // actual bytes instead of treating an uploaded dataset as absent.
            List<InputRequirement> inputs = requested == null ? List.of() : requested.inputs();
            // Always retain immutable task/document inputs, even when the
            // planner supplied additional manifest inputs.  Otherwise a
            // planner could accidentally omit an embedded image from hash
            // verification while the file remained mounted in the sandbox.
            inputs = mergeInputRequirements(inputs, taskInputRequirements(state));
            List<String> runtimeCoordinates = dependencyCoordinates(runtimeRequest);
            List<String> coordinates = !runtimeCoordinates.isEmpty()
                    ? runtimeCoordinates
                    : requested == null ? List.of()
                    : requested.dependencies().stream()
                    .map(value -> value.name() + "==" + value.version()).toList();
            EnvironmentManifest resolved = resolver.resolve(language, entryPoint,
                    new PythonDependencyRequest(coordinates), inputs);
            boolean provisionalManifest = requested != null && requested.dependencies().isEmpty()
                    && !runtimeCoordinates.isEmpty();
            if (requested != null && !provisionalManifest && (!resolved.imageDigest().equals(requested.imageDigest())
                    || !resolved.capabilityProfile().equals(requested.capabilityProfile())
                    || !resolved.dependencies().equals(requested.dependencies()))) {
                throw new NonRetryableTaskException("ENV_MANIFEST_NOT_CATALOG_RESOLVED: manifest is not the catalog result");
            }
            persistResolvedManifest(state, runtimeRequest, resolved);
        } catch (NonRetryableTaskException failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw new NonRetryableTaskException("ENV_CATALOG_RESOLUTION_FAILED: " + bound(invalid.getMessage(), 256));
        }
    }

    private static void persistResolvedManifest(Map<String, Object> state, Object runtimeRequest,
                                                EnvironmentManifest resolved) {
        Object previousDigest = state.get("environmentManifestDigest");
        if (previousDigest != null && !resolved.manifestDigest().equals(String.valueOf(previousDigest))) {
            throw new NonRetryableTaskException("ENV_MANIFEST_CHANGED: environment manifest changed during retry");
        }
        state.put("environmentManifest", resolved);
        state.put("environmentManifestDigest", resolved.manifestDigest());
        if (runtimeRequest instanceof Map<?, ?> request) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            request.forEach((key, value) -> {
                if (key instanceof String name && !Set.of("environmentManifest", "dependencies",
                        "packages", "resolvedDependencies", "imageDigest", "capabilityProfile").contains(name)) {
                    sanitized.put(name, value);
                }
            });
            sanitized.put("resolvedDependencies", resolved.dependencies().stream()
                    .map(value -> Map.of("name", value.name(), "version", value.version(),
                            "sha256", String.valueOf(value.sha256())))
                    .toList());
            state.put("runtimeRequest", sanitized);
        }
    }

    static List<InputRequirement> taskInputRequirements(Map<String, Object> state) {
        // Explicit task inputs and immutable DOCX assets share one preflight
        // contract.  Deduplicate by relative path so a planner cannot weaken a
        // hash/size assertion by repeating an asset under a second declaration.
        Map<String, InputRequirement> byPath = new LinkedHashMap<>();
        appendInputRequirements(byPath, state.get("taskInputs"), "taskInput");
        appendInputRequirements(byPath, state.get("documentAssetInputs"), "documentAsset");
        appendInputRequirements(byPath, state.get("sourceDocumentInput"), "sourceDocument");
        return List.copyOf(byPath.values());
    }

    private static List<InputRequirement> mergeInputRequirements(List<InputRequirement> requested,
                                                                  List<InputRequirement> immutable) {
        Map<String, InputRequirement> byPath = new LinkedHashMap<>();
        if (requested != null) {
            for (InputRequirement requirement : requested) {
                if (requirement == null) throw new IllegalArgumentException("input requirement is null");
                byPath.put(requirement.relativePath(), requirement);
            }
        }
        if (immutable != null) {
            for (InputRequirement requirement : immutable) {
                if (requirement == null) throw new IllegalArgumentException("input requirement is null");
                InputRequirement previous = byPath.putIfAbsent(requirement.relativePath(), requirement);
                if (previous != null && (!java.util.Objects.equals(previous.sha256(), requirement.sha256())
                        || previous.maxBytes() != requirement.maxBytes()
                        || previous.kind() != requirement.kind())) {
                    throw new IllegalArgumentException("input requirement conflicts for path: "
                            + requirement.relativePath());
                }
            }
        }
        return List.copyOf(byPath.values());
    }

    private static void appendInputRequirements(Map<String, InputRequirement> target, Object raw,
                                                 String fieldPrefix) {
        List<?> values;
        if (raw instanceof List<?> list) {
            values = list;
        } else if (raw instanceof Map<?, ?> map) {
            // Source DOCX metadata is a single canonical record while task and
            // embedded-asset inputs are represented as lists.  Accept both
            // shapes so the immutable preflight contract remains explicit.
            values = List.of(map);
        } else {
            return;
        }
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> input)) {
                throw new IllegalArgumentException(fieldPrefix + " metadata is invalid");
            }
            String path = stringValue(input.get("relativePath"), fieldPrefix + ".relativePath");
            long size = longValue(input.get("size"), 0L);
            String sha256 = stringValue(input.get("sha256"), fieldPrefix + ".sha256");
            InputRequirement requirement = new InputRequirement(path, InputRequirement.Kind.FILE, size, sha256);
            InputRequirement previous = target.putIfAbsent(path, requirement);
            if (previous != null && (!previous.sha256().equals(requirement.sha256())
                    || previous.maxBytes() != requirement.maxBytes())) {
                throw new IllegalArgumentException("input metadata conflicts for path: " + path);
            }
        }
    }

    private static String runtimeEntryPoint(Object runtimeRequest) {
        if (runtimeRequest instanceof Map<?, ?> values && values.get("entryPoint") instanceof String value
                && !value.isBlank()) {
            return value;
        }
        return "main.py";
    }

    private static List<String> dependencyCoordinates(Object runtimeRequest) {
        if (!(runtimeRequest instanceof Map<?, ?> values)) return List.of();
        Object raw = values.get("dependencies");
        if (raw == null) raw = values.get("packages");
        if (raw == null) raw = values.get("resolvedDependencies");
        if (!(raw instanceof List<?> list)) {
            if (raw == null) return List.of();
            throw new IllegalArgumentException("runtime dependencies must be an array");
        }
        List<String> coordinates = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof String coordinate) {
                coordinates.add(coordinate);
            } else if (value instanceof Map<?, ?> dependency
                    && dependency.get("name") instanceof String name
                    && dependency.get("version") instanceof String version) {
                coordinates.add(name + "==" + version);
            } else {
                throw new IllegalArgumentException("runtime dependency coordinate is invalid");
            }
        }
        return List.copyOf(coordinates);
    }

    private EnvironmentManifest manifest(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("environmentManifest must be an object");
        }
        int schemaVersion = intValue(values.get("schemaVersion"), 1);
        String language = stringValue(values.get("language"), "language");
        String entryPoint = stringValue(values.get("entryPoint"), "entryPoint");
        String imageDigest = stringValue(values.get("imageDigest"), "imageDigest");
        String capabilityProfile = values.get("capabilityProfile") == null ? "default"
                : stringValue(values.get("capabilityProfile"), "capabilityProfile");
        List<DependencySpec> dependencies = new ArrayList<>();
        Object dependencyValue = values.get("dependencies");
        if (dependencyValue instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> dependency)) throw new IllegalArgumentException("dependency is invalid");
                dependencies.add(new DependencySpec(stringValue(dependency.get("name"), "dependency.name"),
                        stringValue(dependency.get("version"), "dependency.version"),
                        dependency.get("sha256") == null ? null : stringValue(dependency.get("sha256"), "dependency.sha256")));
            }
        } else if (dependencyValue != null) {
            throw new IllegalArgumentException("dependencies must be an array");
        }
        List<InputRequirement> inputs = new ArrayList<>();
        Object inputValue = values.get("inputs");
        if (inputValue instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> input)) throw new IllegalArgumentException("input is invalid");
                InputRequirement.Kind kind = input.get("kind") == null ? InputRequirement.Kind.FILE
                        : InputRequirement.Kind.valueOf(stringValue(input.get("kind"), "input.kind").toUpperCase(Locale.ROOT));
                long maxBytes = longValue(input.get("maxBytes"), 1L);
                String hash = input.get("sha256") == null ? null : stringValue(input.get("sha256"), "input.sha256");
                inputs.add(new InputRequirement(stringValue(input.get("relativePath"), "input.relativePath"), kind, maxBytes, hash));
            }
        } else if (inputValue != null) {
            throw new IllegalArgumentException("inputs must be an array");
        }
        String digest = values.get("manifestDigest") == null ? null : stringValue(values.get("manifestDigest"), "manifestDigest");
        return new EnvironmentManifest(schemaVersion, language, entryPoint, dependencies, inputs,
                imageDigest, capabilityProfile, digest);
    }

    private static String stringValue(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 512) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return text.trim();
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.intValue() != number.doubleValue()) {
            throw new IllegalArgumentException("schemaVersion is invalid");
        }
        return number.intValue();
    }

    private static long longValue(Object value, long fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.longValue() != number.doubleValue()) {
            throw new IllegalArgumentException("numeric manifest field is invalid");
        }
        return number.longValue();
    }

    @PreDestroy
    void shutdownAgentProgressHeartbeats() {
        agentProgressHeartbeats.shutdownNow();
    }

    private void export(Document document, ReportTaskPayload payload, TaskExecutionContext context,
                        Map<String, Object> state) {
        BoundedOutputStream output = new BoundedOutputStream(MAX_EXPORT_BYTES);
        AssetResolver resolver = assetResolver(document, payload);
        AssetResolver sourceResolver = sourceDocumentResolver(document, payload);
        List<TemplatePatch> patches = templatePatches(state);
        WordBuildOptions defaults = WordBuildOptions.defaults();
        ReleaseResult result = wordRelease.release(document, output,
                new WordBuildOptions(defaults.maxAssetBytes(), defaults.maxImageWidth(), defaults.maxImageHeight(),
                        defaults.allowedImageMediaTypes(), resolver, sourceResolver, patches));
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
        for (ObjectIntegrity existing : objectIntegrity.findByOwner(payload.tenantId(), "TASK", payload.taskId())) {
            if ("OUTPUT".equals(existing.role())) {
                if (!outputReference.equals(existing.object())) {
                    throw new ReportTaskFailure("immutable task output changed during retry", null);
                }
                context.progress(95, "DOCX export already persisted", Map.of("bytes", String.valueOf(output.size())));
                return;
            }
        }
        storage.put(outputReference, new java.io.ByteArrayInputStream(bytes));
        objectIntegrity.save(new ObjectIntegrity(ids.nextId(), payload.tenantId(), "TASK", payload.taskId(),
                "OUTPUT", outputReference, java.time.Instant.now()));
        context.progress(95, "DOCX export generated", Map.of("bytes", String.valueOf(output.size())));
    }

    private AssetResolver sourceDocumentResolver(Document document, ReportTaskPayload payload) {
        ObjectStorageResolver resolver = storageResolvers.getIfAvailable();
        if (resolver == null || document == null) return AssetResolver.none();
        return key -> {
            if (!document.sourceObjectKey().equals(key)) return java.util.Optional.empty();
            StoredObject reference = new StoredObject(document.sourceObjectKey(), document.mediaType(),
                    payload.size(), payload.sha256());
            return java.util.Optional.of(new AssetResolver.Asset(document.mediaType(),
                    resolver.open(payload.tenantId(), reference), payload.size()));
        };
    }

    /** Converts the Document Agent's strict JSON patch into local template edits. */
    private List<TemplatePatch> templatePatches(Map<String, Object> state) {
        Object raw = state.get("documentDraft");
        if (raw == null) return List.of();
        try {
            JsonNode root = raw instanceof String text ? readDraftJson(text)
                    : objectMapper.valueToTree(raw);
            JsonNode answers = root.isArray() ? root : root.path("answers");
            List<TemplatePatch> patches = new ArrayList<>();
            if (answers.isArray()) {
                for (JsonNode answer : answers) {
                    String anchor = answer.path("anchor").asText("").strip();
                    if (anchor.isBlank()) anchor = answer.path("question").asText("").strip();
                    String value = answer.path("answer").asText("").strip();
                    if (!anchor.isBlank() && !value.isBlank()) {
                        patches.add(new TemplatePatch(anchor, value, keys(answer.path("figureAssetKeys"))));
                    }
                }
            }
            List<String> images = artifactImageKeys(state.get("artifactReferences"));
            if (!images.isEmpty()) {
                String anchor = "实验结果与分析";
                boolean attached = patches.stream().anyMatch(p -> p.anchor().contains(anchor));
                if (!attached) patches.add(new TemplatePatch(anchor, "", images));
            }
            return List.copyOf(patches);
        } catch (Exception invalid) {
            // A malformed model draft is not allowed to become visible prose or
            // corrupt the template. The retained source is still exportable.
            return List.of();
        }
    }

    private JsonNode readDraftJson(String text) throws JsonProcessingException {
        String normalized = text == null ? "" : text.strip();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLine = normalized.indexOf('\n');
            normalized = firstLine >= 0 ? normalized.substring(firstLine + 1, normalized.length() - 3).strip()
                    : normalized.substring(3, normalized.length() - 3).strip();
        }
        return objectMapper.readTree(normalized);
    }

    private static List<String> keys(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> keys = new ArrayList<>();
        node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) keys.add(value.asText()); });
        return List.copyOf(keys);
    }

    private static List<String> artifactImageKeys(Object raw) {
        if (!(raw instanceof List<?> values)) return List.of();
        List<String> keys = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> ref)) continue;
            Object type = ref.get("mediaType");
            Object key = ref.get("objectKey");
            if (type != null && String.valueOf(type).toLowerCase(Locale.ROOT).startsWith("image/")
                    && key != null && !String.valueOf(key).isBlank()) keys.add(String.valueOf(key));
        }
        return List.copyOf(keys);
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
            if (asset.objectKey().startsWith("tasks/")) {
                ArtifactStorageResolver artifacts = artifactStorageResolvers.getIfAvailable();
                if (artifacts == null) return java.util.Optional.empty();
                return java.util.Optional.of(new AssetResolver.Asset(asset.mediaType(),
                        artifacts.open(payload.tenantId(), reference), asset.size()));
            }
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

    /**
     * Persists the first resolved environment digest on the task aggregate.
     * Checkpoint JSON remains the detailed execution record, while this column
     * is the task-level immutable anchor used by retries, exports and audits.
     */
    private void lockTaskManifestDigest(ReportTaskPayload payload, Map<String, Object> state) {
        Object raw = state.get("environmentManifestDigest");
        if (raw == null && state.get("environmentManifest") instanceof EnvironmentManifest manifest) {
            raw = manifest.manifestDigest();
        }
        if (!(raw instanceof String digest) || digest.isBlank()) {
            if (runtimeProperties.isEnabled()) {
                throw new NonRetryableTaskException("ENV_MANIFEST_MISSING: resolved manifest digest is required");
            }
            return;
        }
        for (int attempt = 0; attempt < MAX_TRANSITION_RETRIES; attempt++) {
            ReportTask current = tasks.findById(payload.tenantId(), payload.taskId())
                    .orElseThrow(() -> new ReportTaskFailure("report task does not exist", null));
            try {
                ReportTask next = current.lockManifestDigest(digest);
                if (next == current || tasks.update(next, current.version())) {
                    return;
                }
            } catch (IllegalStateException immutable) {
                throw new NonRetryableTaskException("ENV_MANIFEST_CHANGED: task manifest digest is immutable");
            }
        }
        throw new ReportTaskFailure("report task version conflict while locking manifest", null);
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

    /** Adds only the tool's bounded, already-redacted diagnostic to the task error. */
    private static String failureMessage(String message, Object toolErrorCode,
                                         Map<String, Object> outputs) {
        if (toolErrorCode == null || outputs == null) {
            return message;
        }
        Object raw = outputs.get("toolStderrSummary");
        if (!(raw instanceof String diagnostic) || diagnostic.isBlank()) {
            return message;
        }
        return bound(message + ": " + diagnostic, 512);
    }

    private static String reviewFailureMessage(String message, Map<String, Object> outputs) {
        Object raw = outputs == null ? null : outputs.get("reviewFindings");
        if (!(raw instanceof List<?> findings) || findings.isEmpty()) {
            return bound("REVIEW_INCOMPLETE: " + message, 512);
        }
        String summary = findings.stream().limit(8).map(String::valueOf)
                .reduce((left, right) -> left + " | " + right).orElse("");
        return bound("REVIEW_INCOMPLETE: " + message + ": " + summary, 512);
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
        if (failure instanceof ReportTaskFailure || failure instanceof NonRetryableTaskException) {
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
