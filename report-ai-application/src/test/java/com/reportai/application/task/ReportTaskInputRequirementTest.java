package com.reportai.application.task;

import com.reportai.runtime.InputRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTaskInputRequirementTest {
    @Test
    void mapsMaterializedTaskInputsToHashLockedFileRequirements() {
        String sha256 = "a".repeat(64);
        List<InputRequirement> requirements = ReportTaskHandler.taskInputRequirements(Map.of(
                "taskInputs", List.of(Map.of(
                        "relativePath", "data/dataset.zip",
                        "size", 4096L,
                        "sha256", sha256))));

        assertEquals(1, requirements.size());
        InputRequirement requirement = requirements.getFirst();
        assertEquals("data/dataset.zip", requirement.relativePath());
        assertEquals(InputRequirement.Kind.FILE, requirement.kind());
        assertEquals(4096L, requirement.maxBytes());
        assertEquals(sha256, requirement.sha256());
    }

    @Test
    void returnsNoRequirementsWhenTaskHasNoUploadedInputs() {
        assertTrue(ReportTaskHandler.taskInputRequirements(Map.of()).isEmpty());
    }

    @Test
    void includesEmbeddedDocumentAssetsInTheSameHashLockedContract() {
        String taskHash = "a".repeat(64);
        String assetHash = "b".repeat(64);
        List<InputRequirement> requirements = ReportTaskHandler.taskInputRequirements(Map.of(
                "taskInputs", List.of(Map.of("relativePath", "data/dataset.zip",
                        "size", 4096L, "sha256", taskHash)),
                "documentAssetInputs", List.of(Map.of("relativePath", "document-assets/001-" + assetHash.substring(0, 12) + "-wbs.png",
                        "size", 2048L, "sha256", assetHash))));

        assertEquals(2, requirements.size());
        assertEquals("document-assets/001-" + assetHash.substring(0, 12) + "-wbs.png",
                requirements.get(1).relativePath());
        assertEquals(assetHash, requirements.get(1).sha256());
    }

    @Test
    void includesCanonicalSourceDocxAsAHashLockedRuntimeInput() {
        String sourceHash = "c".repeat(64);
        List<InputRequirement> requirements = ReportTaskHandler.taskInputRequirements(Map.of(
                "sourceDocumentInput", Map.of(
                        "relativePath", ReportTaskHandler.SOURCE_DOCUMENT_INPUT_PATH,
                        "mediaType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "size", 55_321L,
                        "sha256", sourceHash,
                        "objectKey", "documents/tenant/report.docx",
                        "documentId", "doc-1")));

        assertEquals(1, requirements.size());
        InputRequirement requirement = requirements.getFirst();
        assertEquals("source-document.docx", requirement.relativePath());
        assertEquals(InputRequirement.Kind.FILE, requirement.kind());
        assertEquals(55_321L, requirement.maxBytes());
        assertEquals(sourceHash, requirement.sha256());
    }
}
