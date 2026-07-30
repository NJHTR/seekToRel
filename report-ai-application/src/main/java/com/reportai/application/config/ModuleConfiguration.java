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
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import com.reportai.tool.BuiltInToolRegistry;
import com.reportai.tool.ToolRegistry;

@Configuration
public class ModuleConfiguration {
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
    public DockerSandboxClient dockerSandboxClient() {
        return DockerJavaSandboxClient.connectFromEnvironment();
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public RuntimeEngine runtimeEngine(DockerSandboxClient client, ReportAiRuntimeProperties properties) {
        RuntimeConfiguration configuration = properties.toConfiguration();
        return new DockerRuntimeEngine(client, configuration);
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.runtime", name = "enabled", havingValue = "true")
    public ToolRegistry builtInToolRegistry(RuntimeEngine runtimeEngine) {
        return BuiltInToolRegistry.create(runtimeEngine);
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ObjectStorage objectStorage(ReportAiObjectStorageProperties properties,
                                       ReportAiUploadProperties uploadProperties) {
        return MinioObjectStorage.create(new ObjectStorageProperties(
                properties.getEndpoint(), properties.getAccessKey(), properties.getSecretKey(),
                properties.getBucket(), properties.getRegion(), properties.isPathStyle(),
                uploadProperties.getMaxBytes()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ObjectStorageResolver objectStorageResolver(ObjectStorage objectStorage) {
        return new TenantObjectStorageResolver(objectStorage);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.object-storage", name = "enabled", havingValue = "true")
    public ObjectIntegrityReconciler objectIntegrityReconciler(
            @Qualifier("objectIntegrityReconciliationRepository")
            com.reportai.storage.ObjectIntegrityReconciliationRepository repository,
            ObjectStorageResolver resolver,
            ReportAiObjectStorageProperties properties) {
        return new ObjectIntegrityReconciler(repository, resolver, properties);
    }
}
