package com.reportai.application.task;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.EnvironmentManifest;
import com.reportai.review.CompletenessReviewEngine;
import com.reportai.word.DefaultWordReleaseService;
import com.reportai.word.PoiWordBuilder;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReportDocumentAssemblyServiceTest {
    @Test
    void assemblesVerifiedRuntimeStateAndImageArtifacts() {
        Document source = new Document("doc-1", TenantId.of("tenant-1"), "documents/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "实验报告", List.of(), null)), List.of(), List.of());
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("numpy", "1.26.4", "a".repeat(64))), List.of(),
                "python@sha256:" + "b".repeat(64), "scientific");
        Map<String, Object> state = Map.of(
                "code", "print('ok')",
                "environmentManifest", manifest,
                "execution", Map.of("status", "SUCCEEDED", "exitCode", 0, "stdout", "ok", "stderr", ""),
                "artifactReferences", List.of(Map.of("logicalKey", "plots/loss.png",
                        "objectKey", "tasks/hash/task/artifacts/hash/plots/loss.png",
                        "mediaType", "image/png", "size", 3, "sha256", "c".repeat(64))),
                "documentDraft", "draft explanation");

        Document assembled = new ReportDocumentAssemblyService().assemble(source, state);

        assertEquals(1, assembled.assets().size());
        assertEquals("loss.png", assembled.assets().get(0).originalFileName());
        assertTrue(assembled.blocks().stream().anyMatch(block -> block.type() == BlockType.CODE
                && "print('ok')".equals(block.text())));
        assertTrue(assembled.blocks().stream().anyMatch(block -> block.type() == BlockType.IMAGE
                && "tasks/hash/task/artifacts/hash/plots/loss.png".equals(block.assetKey())));
        assertFalse(assembled.blocks().stream().anyMatch(block -> "draft explanation".equals(block.text())));
    }

    @Test
    void doesNotInventExecutionWhenStateIsMissing() {
        Document source = new Document("doc-1", TenantId.of("tenant-1"), "documents/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "实验报告", List.of(), null)), List.of(), List.of());

        Document assembled = new ReportDocumentAssemblyService().assemble(source, Map.of());

        assertTrue(assembled.blocks().stream().anyMatch(block -> block.text() != null
                && block.text().contains("执行结果缺失")));
        assertTrue(assembled.assets().isEmpty());
    }

    @Test
    void rendersManifestRestoredFromJsonCheckpointMap() {
        Document source = new Document("doc-1", TenantId.of("tenant-1"), "documents/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "实验报告", List.of(), null)), List.of(), List.of());
        Document assembled = new ReportDocumentAssemblyService().assemble(source, Map.of(
                "environmentManifest", Map.of("manifestDigest", "d".repeat(64),
                        "imageDigest", "python@sha256:" + "e".repeat(64), "language", "python",
                        "entryPoint", "main.py", "dependencies", List.of(Map.of(
                                "name", "numpy", "version", "1.26.4", "sha256", "f".repeat(64)))),
                "execution", Map.of("status", "SUCCEEDED")));

        assertTrue(assembled.blocks().stream().anyMatch(block -> block.text() != null
                && block.text().contains("manifestDigest: " + "d".repeat(64))));
        assertTrue(assembled.blocks().stream().anyMatch(block -> block.text() != null
                && block.text().contains("numpy==1.26.4")));
    }

    @Test
    void assembledDocumentReleasesAsDocxWithVerifiedSections() throws Exception {
        Document source = new Document("doc-1", TenantId.of("tenant-1"), "documents/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "实验报告", List.of(), null)), List.of(), List.of());
        Document assembled = new ReportDocumentAssemblyService().assemble(source, Map.of(
                "code", "print('reproducible')",
                "environmentManifestDigest", "a".repeat(64),
                "execution", Map.of("status", "SUCCEEDED", "exitCode", 0, "stdout", "done")));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var release = new DefaultWordReleaseService(new CompletenessReviewEngine(), new PoiWordBuilder())
                .release(assembled, bytes);

        assertTrue(release.released());
        try (XWPFDocument exported = new XWPFDocument(new ByteArrayInputStream(bytes.toByteArray()))) {
            String text = exported.getParagraphs().stream().map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("print('reproducible')"));
            assertTrue(text.contains("manifestDigest: " + "a".repeat(64)));
            assertTrue(text.contains("status: SUCCEEDED"));
        }
    }

    /** Writes a deterministic QA artifact for the document render gate. */
    @Test
    void writesRenderGateArtifact() throws Exception {
        Document source = new Document("doc-render", TenantId.of("tenant-render"), "documents/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(
                        new DocumentBlock(BlockType.TITLE, "Nexus Report Forge - GAN/CycleGAN Experiment", List.of(), null),
                        new DocumentBlock(BlockType.HEADING, "Verified execution", List.of(), null),
                        new DocumentBlock(BlockType.PARAGRAPH, "This page is generated from verified runtime state.", List.of(), null)),
                List.of(), List.of());
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("torch", "2.5.1+cpu", "a".repeat(64))), List.of(),
                "reportai/python-scientific@sha256:" + "b".repeat(64), "scientific");
        Document assembled = new ReportDocumentAssemblyService().assemble(source, Map.of(
                "code", "print('gan_cycle_smoke_ok')",
                "environmentManifest", manifest,
                "taskInputDigest", "c".repeat(64),
                "taskInputs", List.of(Map.of("relativePath", "domain_a.csv", "size", 32, "sha256", "d".repeat(64))),
                "execution", Map.of("status", "SUCCEEDED", "exitCode", 0,
                        "stdout", "gan_cycle_smoke_ok", "stderr", "")));
        Path target = Path.of("target", "qa", "render-gate.docx");
        Files.createDirectories(target.getParent());
        try (var output = Files.newOutputStream(target)) {
            var release = new DefaultWordReleaseService(new CompletenessReviewEngine(), new PoiWordBuilder())
                    .release(assembled, output);
            assertTrue(release.released());
        }
        assertTrue(Files.size(target) > 0);
    }
}
