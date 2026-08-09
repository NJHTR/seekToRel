package com.reportai.application.config;

import com.reportai.application.config.properties.ReportAiLlmProperties;
import com.reportai.application.config.properties.ReportAiCatalogProperties;
import com.reportai.application.config.properties.ReportAiMemorySnapshotProperties;
import com.reportai.application.config.properties.ReportAiObjectStorageProperties;
import com.reportai.application.config.properties.ReportAiObservabilityProperties;
import com.reportai.application.config.properties.ReportAiParserProperties;
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.config.properties.ReportAiUploadProperties;
import org.springframework.beans.factory.InitializingBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Fails fast on unsafe or internally inconsistent production settings. */
@Component
public final class StartupConfigurationValidator implements InitializingBean {
    private static final Pattern BUCKET_NAME = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupConfigurationValidator.class);

    private final ReportAiUploadProperties upload;
    private final ReportAiParserProperties parser;
    private final ReportAiRuntimeProperties runtime;
    private final ReportAiCatalogProperties catalog;
    private final ReportAiObjectStorageProperties objectStorage;
    private final ReportAiLlmProperties llm;
    private final ReportAiObservabilityProperties observability;
    private final ReportAiSchedulerProperties scheduler;
    private final ReportAiMemorySnapshotProperties memorySnapshot;

    public StartupConfigurationValidator(ReportAiUploadProperties upload,
                                         ReportAiParserProperties parser,
                                         ReportAiRuntimeProperties runtime,
                                         ReportAiCatalogProperties catalog,
                                         ReportAiObjectStorageProperties objectStorage,
                                         ReportAiLlmProperties llm,
                                         ReportAiObservabilityProperties observability,
                                         ReportAiSchedulerProperties scheduler,
                                         ReportAiMemorySnapshotProperties memorySnapshot) {
        this.upload = upload;
        this.parser = parser;
        this.runtime = runtime;
        this.catalog = catalog;
        this.objectStorage = objectStorage;
        this.llm = llm;
        this.observability = observability;
        this.scheduler = scheduler;
        this.memorySnapshot = memorySnapshot;
    }

    @Override
    public void afterPropertiesSet() {
        validateUpload();
        validateParser();
        validateRuntime();
        validateCatalog();
        validateObjectStorage();
        validateLlm();
        validateObservability();
        validateScheduler();
        validateMemorySnapshot();
    }

    private void validateUpload() {
        requirePositive("report-ai.upload.max-bytes", upload.getMaxBytes());
        if (upload.getMaxFileNameLength() < 1 || upload.getMaxFileNameLength() > 255) {
            invalid("report-ai.upload.max-file-name-length", "must be between 1 and 255");
        }
        requireNonEmpty("report-ai.upload.allowed-media-types", upload.getAllowedMediaTypes());
        requireNonEmpty("report-ai.upload.allowed-extensions", upload.getAllowedExtensions());
        upload.getAllowedMediaTypes().forEach(value -> requireText("report-ai.upload.allowed-media-types", value));
        upload.getAllowedExtensions().forEach(value -> {
            requireText("report-ai.upload.allowed-extensions", value);
            if (!value.startsWith(".")) {
                invalid("report-ai.upload.allowed-extensions", "every extension must start with '.'");
            }
        });
    }

    private void validateParser() {
        requirePositive("report-ai.parser.max-input-bytes", parser.getMaxInputBytes());
        requirePositive("report-ai.parser.max-zip-entries", parser.getMaxZipEntries());
        requirePositive("report-ai.parser.max-uncompressed-bytes", parser.getMaxUncompressedBytes());
        requirePositive("report-ai.parser.max-single-entry-bytes", parser.getMaxSingleEntryBytes());
        requirePositive("report-ai.parser.max-xml-entry-bytes", parser.getMaxXmlEntryBytes());
        requirePositive("report-ai.parser.max-blocks", parser.getMaxBlocks());
        requirePositive("report-ai.parser.max-text-chars", parser.getMaxTextChars());
        requirePositive("report-ai.parser.max-compression-ratio", parser.getMaxCompressionRatio());
        if (parser.getMaxSingleEntryBytes() > parser.getMaxUncompressedBytes()
                || parser.getMaxXmlEntryBytes() > parser.getMaxSingleEntryBytes()) {
            invalid("report-ai.parser", "entry limits must be non-increasing");
        }
    }

    private void validateRuntime() {
        requirePositive("report-ai.runtime.timeout", runtime.getTimeout());
        requirePositive("report-ai.runtime.max-output-bytes", runtime.getMaxOutputBytes());
        requirePositive("report-ai.runtime.cpu-limit", runtime.getCpuLimit());
        requirePositive("report-ai.runtime.memory-bytes", runtime.getMemoryBytes());
        if (runtime.isEnabled()) {
            requireText("report-ai.runtime.workspace-root", runtime.getWorkspaceRoot());
            requirePositive("report-ai.runtime.max-artifacts", runtime.getMaxArtifacts());
            requirePositive("report-ai.runtime.max-artifact-bytes", runtime.getMaxArtifactBytes());
            requirePositive("report-ai.runtime.max-input-bytes", runtime.getMaxInputBytes());
            requirePositive("report-ai.runtime.max-cpu-limit", runtime.getMaxCpuLimit());
            requirePositive("report-ai.runtime.max-memory-bytes", runtime.getMaxMemoryBytes());
            requirePositive("report-ai.runtime.max-timeout", runtime.getMaxTimeout());
        } else {
            LOGGER.warn("runtime engine is disabled; generated code cannot execute in the Docker sandbox");
        }
    }

    /**
     * Runtime execution is reproducible only when every container image and
     * dependency coordinate comes from the administrator-signed catalog.  Do
     * not defer this invariant to task execution: a missing resolver used to
     * permit a legacy sandbox run and fail only while locking the task digest.
     */
    private void validateCatalog() {
        if (!catalog.isEnabled()) {
            if (runtime.isEnabled()) {
                invalid("report-ai.runtime.catalog.enabled",
                        "must be true when the sandbox runtime is enabled because every execution requires a signed environment manifest");
            }
            return;
        }
        if (!runtime.isEnabled()) {
            invalid("report-ai.runtime.catalog.enabled",
                    "must be false when the sandbox runtime is disabled");
        }
        requireText("report-ai.runtime.catalog.path", catalog.getPath());
        requireText("report-ai.runtime.catalog.signature-path", catalog.getSignaturePath());
        if (!StringUtils.hasText(catalog.getPublicKey()) && !StringUtils.hasText(catalog.getPublicKeyPath())) {
            invalid("report-ai.runtime.catalog", "public-key or public-key-path must be configured");
        }
        if (catalog.getMaxBytes() <= 0 || catalog.getMaxBytes() > com.reportai.runtime.SignedCatalogVerifier.MAX_PAYLOAD_BYTES) {
            invalid("report-ai.runtime.catalog.max-bytes",
                    "must be between 1 and " + com.reportai.runtime.SignedCatalogVerifier.MAX_PAYLOAD_BYTES);
        }
        LOGGER.info("signed runtime environment catalog enabled catalogPath={} signaturePath={} publicKeySource={}",
                catalog.getPath(), catalog.getSignaturePath(),
                StringUtils.hasText(catalog.getPublicKeyPath()) ? "file" : "inline");
    }

    private void validateObjectStorage() {
        if (!objectStorage.isEnabled()) {
            if (runtime.isEnabled()) {
                invalid("report-ai.object-storage.enabled",
                        "must be true when the sandbox runtime is enabled because execution artifacts are durable inputs to review/export");
            }
            return;
        }
        requireText("report-ai.object-storage.endpoint", objectStorage.getEndpoint());
        requireText("report-ai.object-storage.bucket", objectStorage.getBucket());
        requireText("report-ai.object-storage.access-key", objectStorage.getAccessKey());
        requireText("report-ai.object-storage.secret-key", objectStorage.getSecretKey());
        validateHttpUri("report-ai.object-storage.endpoint", objectStorage.getEndpoint());
        if (!BUCKET_NAME.matcher(objectStorage.getBucket()).matches()) {
            invalid("report-ai.object-storage.bucket", "must be a valid S3-compatible bucket name");
        }
        requirePositive("report-ai.object-storage.connect-timeout", objectStorage.getConnectTimeout());
        requirePositive("report-ai.object-storage.reconciliation-interval", objectStorage.getReconciliationInterval());
        requirePositive("report-ai.object-storage.orphan-retention", objectStorage.getOrphanRetention());
        if (objectStorage.getReconciliationBatchSize() < 1 || objectStorage.getReconciliationBatchSize() > 1000) {
            invalid("report-ai.object-storage.reconciliation-batch-size", "must be between 1 and 1000");
        }
    }

    private void validateLlm() {
        if (!llm.isEnabled()) {
            LOGGER.warn("LLM provider is disabled; report tasks will fail closed before Agent execution. "
                    + "Set REPORT_AI_LLM_ENABLED=true with a runtime-only provider key to enable automation.");
            return;
        }
        requireText("report-ai.llm.provider", llm.getProvider());
        requireText("report-ai.llm.endpoint", llm.getEndpoint());
        requireText("report-ai.llm.model", llm.getModel());
        validateHttpUri("report-ai.llm.endpoint", llm.getEndpoint());
        String provider = llm.getProvider().trim().toLowerCase(Locale.ROOT);
        if (!provider.equals("ollama") && !provider.equals("local")) {
            requireText("report-ai.llm.api-key", llm.getApiKey());
        }
        if (StringUtils.hasText(llm.getReasoningEffort())
                && !Set.of("low", "medium", "high").contains(llm.getReasoningEffort().trim().toLowerCase(Locale.ROOT))) {
            invalid("report-ai.llm.reasoning-effort", "must be low, medium or high when configured");
        }
        if (StringUtils.hasText(llm.getCoderReasoningEffort())
                && !Set.of("low", "medium", "high").contains(llm.getCoderReasoningEffort().trim().toLowerCase(Locale.ROOT))) {
            invalid("report-ai.llm.coder-reasoning-effort", "must be low, medium or high when configured");
        }
        if (StringUtils.hasText(llm.getCoderFallbackReasoningEffort())
                && !Set.of("low", "medium", "high").contains(llm.getCoderFallbackReasoningEffort().trim().toLowerCase(Locale.ROOT))) {
            invalid("report-ai.llm.coder-fallback-reasoning-effort", "must be low, medium or high when configured");
        }
        if (llm.getCoderMaxOutputTokens() < 256 || llm.getCoderMaxOutputTokens() > 16_384) {
            invalid("report-ai.llm.coder-max-output-tokens", "must be between 256 and 16384");
        }
        requirePositive("report-ai.llm.connect-timeout", llm.getConnectTimeout());
        requirePositive("report-ai.llm.read-timeout", llm.getReadTimeout());
        requirePositive("report-ai.llm.coder-timeout", llm.getCoderTimeout());
        if (llm.getCoderTimeout().compareTo(llm.getReadTimeout()) > 0) {
            invalid("report-ai.llm.coder-timeout", "must not exceed read-timeout");
        }
        if (llm.getVisionMaxAttachments() < 1 || llm.getVisionMaxAttachments() > 64) {
            invalid("report-ai.llm.vision-max-attachments", "must be between 1 and 64");
        }
        if (llm.getVisionMaxAttachmentBytes() < 1 || llm.getVisionMaxAttachmentBytes() > 20L * 1024 * 1024) {
            invalid("report-ai.llm.vision-max-attachment-bytes", "must be between 1 and 20 MiB");
        }
        if (llm.getVisionMaxAttachmentTotalBytes() < llm.getVisionMaxAttachmentBytes()
                || llm.getVisionMaxAttachmentTotalBytes() > 100L * 1024 * 1024) {
            invalid("report-ai.llm.vision-max-attachment-total-bytes",
                    "must be at least one image limit and no more than 100 MiB");
        }
        LOGGER.info("LLM provider configured provider={} model={} reasoningEffort={} coderReasoningEffort={} "
                        + "coderFallbackReasoningEffort={} coderFallbackEnabled={} coderTimeout={} readTimeout={} "
                        + "coderMaxOutputTokens={} visionMaxAttachments={} visionMaxAttachmentBytes={} "
                        + "visionMaxAttachmentTotalBytes={}",
                llm.getProvider(), llm.getModel(), llm.getReasoningEffort(), llm.getCoderReasoningEffort(),
                llm.getCoderFallbackReasoningEffort(), llm.isCoderFallbackEnabled(), llm.getCoderTimeout(),
                llm.getReadTimeout(), llm.getCoderMaxOutputTokens(), llm.getVisionMaxAttachments(),
                llm.getVisionMaxAttachmentBytes(), llm.getVisionMaxAttachmentTotalBytes());
    }

    private void validateObservability() {
        requirePositive("report-ai.observability.slow-request-threshold",
                observability.getSlowRequestThreshold());
        if (observability.getMaxPathLength() < 32 || observability.getMaxPathLength() > 4096) {
            invalid("report-ai.observability.max-path-length", "must be between 32 and 4096");
        }
    }

    private void validateScheduler() {
        // SchedulerConfig owns the worker/queue/lease/execution bounds. Constructing it here
        // keeps invalid production settings from being discovered only on the first dispatch.
        scheduler.toSchedulerConfig();
        requirePositive("report-ai.scheduler.recovery-interval", scheduler.getRecoveryInterval());
        if (scheduler.getRecoveryInitialDelay() == null || scheduler.getRecoveryInitialDelay().isNegative()) {
            invalid("report-ai.scheduler.recovery-initial-delay", "must not be negative");
        }
        if (scheduler.getDispatchBatchSize() < 1 || scheduler.getDispatchBatchSize() > 10_000) {
            invalid("report-ai.scheduler.dispatch-batch-size", "must be between 1 and 10000");
        }
        if (scheduler.getReportTaskMaxAttempts() < 1 || scheduler.getReportTaskMaxAttempts() > 100) {
            invalid("report-ai.scheduler.report-task-max-attempts", "must be between 1 and 100");
        }
        requirePositive("report-ai.scheduler.report-task-initial-backoff", scheduler.getReportTaskInitialBackoff());
        requirePositive("report-ai.scheduler.report-task-max-backoff", scheduler.getReportTaskMaxBackoff());
        if (scheduler.getReportTaskMaxBackoff().compareTo(scheduler.getReportTaskInitialBackoff()) < 0) {
            invalid("report-ai.scheduler.report-task-max-backoff", "must not be less than initial backoff");
        }
        requirePositive("report-ai.scheduler.outbox-published-retention", scheduler.getOutboxPublishedRetention());
        if (scheduler.getOutboxCleanupBatchSize() < 1 || scheduler.getOutboxCleanupBatchSize() > 10_000) {
            invalid("report-ai.scheduler.outbox-cleanup-batch-size", "must be between 1 and 10000");
        }
    }

    private void validateMemorySnapshot() {
        requirePositive("report-ai.memory-snapshot.interval", memorySnapshot.getInterval());
        if (memorySnapshot.getInitialDelay() == null || memorySnapshot.getInitialDelay().isNegative()) {
            invalid("report-ai.memory-snapshot.initial-delay", "must not be negative");
        }
        if (memorySnapshot.getBatchSize() < 1 || memorySnapshot.getBatchSize() > 1_000) {
            invalid("report-ai.memory-snapshot.batch-size", "must be between 1 and 1000");
        }
        if (memorySnapshot.getMaxSummaryChars() < 128 || memorySnapshot.getMaxSummaryChars() > 128 * 1024) {
            invalid("report-ai.memory-snapshot.max-summary-chars", "must be between 128 and 131072");
        }
        if (memorySnapshot.getMaxAttempts() < 1 || memorySnapshot.getMaxAttempts() > 10) {
            invalid("report-ai.memory-snapshot.max-attempts", "must be between 1 and 10");
        }
    }

    private static void validateHttpUri(String property, String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                invalid(property, "must be an http(s) URL without embedded credentials");
            }
        } catch (URISyntaxException exception) {
            invalid(property, "must be a valid URL");
        }
    }

    private static void requireText(String property, String value) {
        if (!StringUtils.hasText(value)) {
            invalid(property, "must not be blank");
        }
    }

    private static void requireNonEmpty(String property, Set<?> value) {
        if (value == null || value.isEmpty()) {
            invalid(property, "must not be empty");
        }
    }

    private static void requirePositive(String property, long value) {
        if (value <= 0) {
            invalid(property, "must be positive");
        }
    }

    private static void requirePositive(String property, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            invalid(property, "must be positive");
        }
    }

    private static void invalid(String property, String message) {
        throw new IllegalStateException("Invalid configuration '" + property + "': " + message);
    }
}
