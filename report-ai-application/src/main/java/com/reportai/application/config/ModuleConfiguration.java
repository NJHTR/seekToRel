package com.reportai.application.config;

import com.reportai.auth.port.AuthorizationPort;
import com.reportai.auth.port.DefaultAuthorizationChecker;
import com.reportai.agent.AgentOrchestrator;
import com.reportai.agent.DefaultAgentOrchestrator;
import com.reportai.application.config.properties.ReportAiObjectStorageProperties;
import com.reportai.application.config.properties.ReportAiParserProperties;
import com.reportai.application.config.properties.ReportAiUploadProperties;
import com.reportai.common.id.UuidIdGenerator;
import com.reportai.parser.DocumentParserPort;
import com.reportai.parser.poi.PoiDocxParser;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.ObjectStorageResolver;
import com.reportai.storage.objectstore.MinioObjectStorage;
import com.reportai.storage.objectstore.ObjectStorageProperties;
import com.reportai.storage.objectstore.TenantObjectStorageResolver;
import com.reportai.storage.ArtifactStorageResolver;
import com.reportai.storage.objectstore.TenantArtifactStorageResolver;
import com.reportai.application.storage.ObjectIntegrityReconciler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.reportai.runtime.DockerJavaSandboxClient;
import com.reportai.runtime.DockerSandboxClient;
import com.reportai.runtime.RuntimeConfiguration;
import com.reportai.runtime.RuntimeEngine;
import com.reportai.runtime.DockerRuntimeEngine;
import com.reportai.runtime.LocalWorkspaceFileStore;
import com.reportai.runtime.WorkspaceFileStore;
import com.reportai.runtime.WorkspaceCleanupReconciler;
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import com.reportai.tool.BuiltInToolRegistry;
import com.reportai.tool.ToolRegistry;
import com.reportai.runtime.EnvironmentCatalog;
import com.reportai.runtime.EnvironmentResolver;
import com.reportai.application.config.properties.ReportAiCatalogProperties;
import com.reportai.application.task.ExecutionArtifactPersistenceService;
import com.reportai.common.id.IdGenerator;

@Configuration
public class ModuleConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime.catalog", name = "enabled", havingValue = "true")
    public EnvironmentCatalog environmentCatalog(ReportAiCatalogProperties properties,
                                                  com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new SignedEnvironmentCatalogLoader(objectMapper).load(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime.catalog", name = "enabled", havingValue = "true")
    public EnvironmentResolver environmentResolver(EnvironmentCatalog catalog) {
        return new EnvironmentResolver(catalog);
    }

    @Bean
    public AgentOrchestrator agentOrchestrator() {
        return new DefaultAgentOrchestrator();
    }

    @Bean
    public AuthorizationPort authorizationPort() {
        return new DefaultAuthorizationChecker();
    }

    @Bean
    public DocumentParserPort docxParser(ReportAiParserProperties properties) {
        return new PoiDocxParser(properties.toParserLimits(), new UuidIdGenerator());
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public DockerSandboxClient dockerSandboxClient(ReportAiRuntimeProperties properties) {
        DockerJavaSandboxClient client = DockerJavaSandboxClient.connectFromEnvironment(properties.isImagePullEnabled());
        client.reconcileOrphanedEphemeralVolumes();
        return client;
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public RuntimeEngine runtimeEngine(DockerSandboxClient client, ReportAiRuntimeProperties properties,
                                       org.springframework.beans.factory.ObjectProvider<EnvironmentCatalog> catalogs) {
        RuntimeConfiguration configuration = properties.toConfiguration();
        EnvironmentCatalog catalog = catalogs.getIfAvailable();
        return new DockerRuntimeEngine(client, catalog == null ? configuration : configuration.withCatalog(catalog));
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public ToolRegistry builtInToolRegistry(RuntimeEngine runtimeEngine,
                                            WorkspaceFileStore workspaceFileStore) {
        return BuiltInToolRegistry.create(runtimeEngine, workspaceFileStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public WorkspaceFileStore workspaceFileStore(ReportAiRuntimeProperties properties) {
        return new LocalWorkspaceFileStore(java.nio.file.Path.of(properties.getWorkspaceRoot()));
    }

    /**
     * Makes sandbox outputs durable before the report review/export stages.  The
     * bean is deliberately conditional on both runtime and object storage: a
     * deployment without either capability must fail the task explicitly rather
     * than silently claiming that a local workspace file is an artifact.
     */
    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ExecutionArtifactPersistenceService executionArtifactPersistenceService(
            ReportAiRuntimeProperties properties, ObjectStorage objectStorage,
            com.reportai.storage.ObjectIntegrityRepository integrityRepository, IdGenerator ids) {
        return new ExecutionArtifactPersistenceService(java.nio.file.Path.of(properties.getWorkspaceRoot()),
                objectStorage, integrityRepository, ids, properties.getMaxArtifactBytes());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public WorkspaceCleanupReconciler workspaceCleanupReconciler(ReportAiRuntimeProperties properties) {
        WorkspaceCleanupReconciler reconciler = new WorkspaceCleanupReconciler(
                java.nio.file.Path.of(properties.getWorkspaceRoot()), properties.getCleanupStaleAfter(),
                properties.getCleanupInterval(), properties.getCleanupMaxEntries());
        reconciler.sweep();
        return reconciler;
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ObjectStorage objectStorage(ReportAiObjectStorageProperties properties,
                                       ReportAiUploadProperties uploadProperties,
                                       ReportAiRuntimeProperties runtimeProperties) {
        long maxObjectBytes = Math.max(uploadProperties.getMaxBytes(),
                Math.max(runtimeProperties.getMaxInputBytes(), runtimeProperties.getMaxArtifactBytes()));
        MinioObjectStorage storage = MinioObjectStorage.create(new ObjectStorageProperties(
                properties.getEndpoint(), properties.getAccessKey(), properties.getSecretKey(),
                properties.getBucket(), properties.getRegion(), properties.isPathStyle(),
                maxObjectBytes));
        if (properties.isCreateBucketIfMissing()) {
            storage.ensureBucketExists();
        }
        return storage;
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ObjectStorageResolver objectStorageResolver(ObjectStorage objectStorage) {
        return new TenantObjectStorageResolver(objectStorage);
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ArtifactStorageResolver artifactStorageResolver(ObjectStorage objectStorage) {
        return new TenantArtifactStorageResolver(objectStorage);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ObjectIntegrityReconciler objectIntegrityReconciler(
            @Qualifier("objectIntegrityReconciliationRepository")
            com.reportai.storage.ObjectIntegrityReconciliationRepository repository,
            ObjectStorageResolver resolver,
            ArtifactStorageResolver artifactResolver,
            ReportAiObjectStorageProperties properties) {
        return new ObjectIntegrityReconciler(repository, resolver, artifactResolver, properties);
    }
}
