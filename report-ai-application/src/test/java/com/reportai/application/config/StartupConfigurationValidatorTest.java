package com.reportai.application.config;

import com.reportai.application.config.properties.ReportAiCatalogProperties;
import com.reportai.application.config.properties.ReportAiLlmProperties;
import com.reportai.application.config.properties.ReportAiMemorySnapshotProperties;
import com.reportai.application.config.properties.ReportAiObjectStorageProperties;
import com.reportai.application.config.properties.ReportAiObservabilityProperties;
import com.reportai.application.config.properties.ReportAiParserProperties;
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.config.properties.ReportAiUploadProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupConfigurationValidatorTest {
    @Test
    void rejectsEnabledRuntimeWithoutSignedCatalogAtStartup() {
        ReportAiRuntimeProperties runtime = runtime(true);
        ReportAiCatalogProperties catalog = catalog(false);

        StartupConfigurationValidator validator = validator(runtime, catalog, objectStorage(true));

        IllegalStateException failure = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(failure.getMessage().contains("report-ai.runtime.catalog.enabled"),
                failure::getMessage);
    }

    @Test
    void acceptsEnabledRuntimeWhenSignedCatalogSettingsAreComplete() {
        ReportAiRuntimeProperties runtime = runtime(true);
        ReportAiCatalogProperties catalog = catalog(true);

        StartupConfigurationValidator validator = validator(runtime, catalog, objectStorage(true));

        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void rejectsCatalogEnabledWhenRuntimeIsDisabled() {
        StartupConfigurationValidator validator = validator(runtime(false), catalog(true), objectStorage(false));

        IllegalStateException failure = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(failure.getMessage().contains("report-ai.runtime.catalog.enabled"),
                failure::getMessage);
    }

    private static StartupConfigurationValidator validator(ReportAiRuntimeProperties runtime,
                                                            ReportAiCatalogProperties catalog,
                                                            ReportAiObjectStorageProperties objectStorage) {
        return new StartupConfigurationValidator(new ReportAiUploadProperties(), new ReportAiParserProperties(),
                runtime, catalog, objectStorage, new ReportAiLlmProperties(),
                new ReportAiObservabilityProperties(), new ReportAiSchedulerProperties(),
                new ReportAiMemorySnapshotProperties());
    }

    private static ReportAiRuntimeProperties runtime(boolean enabled) {
        ReportAiRuntimeProperties properties = new ReportAiRuntimeProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private static ReportAiCatalogProperties catalog(boolean enabled) {
        ReportAiCatalogProperties properties = new ReportAiCatalogProperties();
        properties.setEnabled(enabled);
        if (enabled) {
            properties.setPath("runtime-images/scientific/local-catalog/scientific.catalog.json");
            properties.setSignaturePath("runtime-images/scientific/local-catalog/scientific.catalog.sig");
            properties.setPublicKeyPath("runtime-images/scientific/local-catalog/catalog-ed25519-public.pem");
        }
        return properties;
    }

    private static ReportAiObjectStorageProperties objectStorage(boolean enabled) {
        ReportAiObjectStorageProperties properties = new ReportAiObjectStorageProperties();
        properties.setEnabled(enabled);
        if (enabled) {
            properties.setEndpoint("http://127.0.0.1:19000");
            properties.setBucket("report-ai");
            properties.setAccessKey("test-access");
            properties.setSecretKey("test-secret");
        }
        return properties;
    }
}
