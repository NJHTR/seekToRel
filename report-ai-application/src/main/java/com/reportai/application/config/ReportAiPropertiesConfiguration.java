package com.reportai.application.config;

import com.reportai.application.config.properties.ReportAiLlmProperties;
import com.reportai.application.config.properties.ReportAiMemorySnapshotProperties;
import com.reportai.application.config.properties.ReportAiObjectStorageProperties;
import com.reportai.application.config.properties.ReportAiObservabilityProperties;
import com.reportai.application.config.properties.ReportAiParserProperties;
import com.reportai.application.config.properties.ReportAiProgressProperties;
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.config.properties.ReportAiUploadProperties;
import com.reportai.application.config.properties.ReportAiEmailProperties;
import com.reportai.application.config.properties.ReportAiCatalogProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers all application-owned externalized settings in one place. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ReportAiUploadProperties.class,
        ReportAiParserProperties.class,
        ReportAiRuntimeProperties.class,
        ReportAiObjectStorageProperties.class,
        ReportAiLlmProperties.class,
        ReportAiObservabilityProperties.class,
        ReportAiProgressProperties.class,
        ReportAiSchedulerProperties.class,
        ReportAiMemorySnapshotProperties.class,
        ReportAiEmailProperties.class,
        ReportAiCatalogProperties.class
})
public class ReportAiPropertiesConfiguration {
}
