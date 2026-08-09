package com.reportai.application.task;

import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentAsset;
import com.reportai.document.model.DocumentBlock;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.EnvironmentManifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministically turns verified pipeline state into the document that is reviewed
 * and released.  The LLM may provide a draft, but it cannot manufacture execution
 * evidence: only state produced by the catalog, runtime and artifact persistence
 * boundaries is copied into the report.
 */
final class ReportDocumentAssemblyService {
    private static final int MAX_CODE_CHARS = 512 * 1024;
    private static final int MAX_LOG_CHARS = 2 * 1024 * 1024;
    private static final int MAX_ARTIFACTS = 256;

    Document assemble(Document source, Map<String, Object> state) {
        if (source == null || state == null) {
            throw new IllegalArgumentException("document and state are required");
        }
        List<DocumentBlock> blocks = new ArrayList<>(source.blocks());
        List<DocumentAsset> assets = new ArrayList<>(source.assets());
        blocks.add(new DocumentBlock(BlockType.HEADING, "Nexus 执行结果与可复现信息", List.of(), null));

        appendCode(blocks, state.get("code"));
        appendEnvironment(blocks, state);
        appendExecution(blocks, assets, state);
        // documentDraft remains in the immutable pipeline checkpoint for audit
        // purposes. Raw model prose is deliberately not copied into the released
        // report because it can contain unverified claims.

        return new Document(source.id(), source.tenantId(), source.sourceObjectKey(), source.mediaType(),
                List.copyOf(blocks), source.placeholders(), source.questions(), uniqueAssets(assets));
    }

    private static void appendCode(List<DocumentBlock> blocks, Object value) {
        if (!(value instanceof String code) || code.isBlank()) {
            blocks.add(paragraph("代码未生成：Coder 没有返回可执行源代码。"));
            return;
        }
        blocks.add(heading("生成代码"));
        blocks.add(new DocumentBlock(BlockType.CODE, bounded(code, MAX_CODE_CHARS), List.of(), null));
    }

    private static void appendEnvironment(List<DocumentBlock> blocks, Map<String, Object> state) {
        blocks.add(heading("运行环境"));
        Object manifestValue = state.get("environmentManifest");
        if (manifestValue instanceof EnvironmentManifest manifest) {
            appendManifest(blocks, manifest.manifestDigest(), manifest.imageDigest(), manifest.language(),
                    manifest.entryPoint(), manifest.dependencies());
        } else if (manifestValue instanceof Map<?, ?> values) {
            appendManifestMap(blocks, values, state.get("environmentManifestDigest"));
        } else {
            Object digest = state.get("environmentManifestDigest");
            blocks.add(paragraph("manifestDigest: " + (digest == null ? "unavailable" : digest)));
            blocks.add(paragraph("运行环境清单未锁定，报告不能宣称可复现。"));
        }
        appendTaskInputs(blocks, state);
    }

    private static void appendTaskInputs(List<DocumentBlock> blocks, Map<String, Object> state) {
        Object digest = state.get("taskInputDigest");
        Object raw = state.get("taskInputs");
        if (!(raw instanceof List<?> inputs)) {
            if (digest != null) blocks.add(paragraph("inputDigest: " + text(digest)));
            return;
        }
        blocks.add(paragraph("inputDigest: " + (digest == null ? "unavailable" : text(digest))));
        for (Object value : inputs) {
            if (!(value instanceof Map<?, ?> input)) continue;
            String path = text(input.get("relativePath"));
            String sha = text(input.get("sha256"));
            String size = text(input.get("size"));
            if (!path.isBlank() && !sha.isBlank()) {
                blocks.add(paragraph("input: " + path + " | sha256=" + sha + " | size=" + size));
            }
        }
    }

    private static void appendManifestMap(List<DocumentBlock> blocks, Map<?, ?> values, Object digestFallback) {
        String digest = text(values.get("manifestDigest"));
        if (digest.isBlank()) digest = text(digestFallback);
        String image = text(values.get("imageDigest"));
        String language = text(values.get("language"));
        String entry = text(values.get("entryPoint"));
        blocks.add(paragraph("manifestDigest: " + (digest.isBlank() ? "unavailable" : digest)));
        blocks.add(paragraph("imageDigest: " + (image.isBlank() ? "unavailable" : image)));
        blocks.add(paragraph("language: " + (language.isBlank() ? "unknown" : language)
                + ", entryPoint: " + (entry.isBlank() ? "unknown" : entry)));
        Object dependencies = values.get("dependencies");
        if (!(dependencies instanceof List<?> list) || list.isEmpty()) {
            blocks.add(paragraph("dependencies: none"));
            return;
        }
        for (Object raw : list) {
            if (raw instanceof Map<?, ?> dependency) {
                String name = text(dependency.get("name"));
                String version = text(dependency.get("version"));
                String hash = text(dependency.get("sha256"));
                if (!name.isBlank() && !version.isBlank()) {
                    blocks.add(paragraph("dependency: " + name + "==" + version
                            + " sha256=" + (hash.isBlank() ? "unhashed" : hash)));
                }
            }
        }
    }

    private static void appendManifest(List<DocumentBlock> blocks, String digest, String image,
                                       String language, String entry, List<DependencySpec> dependencies) {
        blocks.add(paragraph("manifestDigest: " + digest));
        blocks.add(paragraph("imageDigest: " + image));
        blocks.add(paragraph("language: " + language + ", entryPoint: " + entry));
        if (dependencies.isEmpty()) {
            blocks.add(paragraph("dependencies: none"));
        } else {
            for (DependencySpec dependency : dependencies) {
                String hash = dependency.sha256() == null ? "unhashed" : dependency.sha256();
                blocks.add(paragraph("dependency: " + dependency.name() + "=="
                        + dependency.version() + " sha256=" + hash));
            }
        }
    }

    private static void appendExecution(List<DocumentBlock> blocks, List<DocumentAsset> assets,
                                        Map<String, Object> state) {
        blocks.add(heading("实验执行结果"));
        Object executionValue = state.get("execution");
        if (!(executionValue instanceof Map<?, ?> execution)) {
            blocks.add(paragraph("执行结果缺失：沙箱没有返回结构化结果。"));
            return;
        }
        String status = text(execution.get("status"));
        blocks.add(paragraph("status: " + (status.isBlank() ? "UNKNOWN" : status)));
        Object exitCode = execution.get("exitCode");
        if (exitCode != null) blocks.add(paragraph("exitCode: " + exitCode));
        appendLog(blocks, "stdout", execution.get("stdout"));
        appendLog(blocks, "stderr", execution.get("stderr"));

        Object references = state.get("artifactReferences");
        if (!(references instanceof List<?> list) || list.isEmpty()) {
            blocks.add(paragraph("artifacts: none"));
            return;
        }
        int count = 0;
        for (Object raw : list) {
            if (count++ >= MAX_ARTIFACTS || !(raw instanceof Map<?, ?> reference)) continue;
            String objectKey = text(reference.get("objectKey"));
            String logicalKey = text(reference.get("logicalKey"));
            String mediaType = text(reference.get("mediaType")).toLowerCase(Locale.ROOT);
            String digest = text(reference.get("sha256"));
            String size = text(reference.get("size"));
            if (objectKey.isBlank() || digest.isBlank()) continue;
            blocks.add(paragraph("artifact: " + (logicalKey.isBlank() ? objectKey : logicalKey)
                    + " | sha256=" + digest + " | size=" + size));
            if (mediaType.startsWith("image/")) {
                try {
                    assets.add(new DocumentAsset(objectKey,
                            fileName(logicalKey.isBlank() ? objectKey : logicalKey),
                            mediaType, longValue(reference.get("size")), digest));
                    blocks.add(new DocumentBlock(BlockType.IMAGE, logicalKey, List.of(), objectKey));
                } catch (RuntimeException ignored) {
                    // Persistence already validated the reference. A malformed
                    // checkpoint must not make export read an unsafe asset.
                }
            }
        }
    }

    private static void appendLog(List<DocumentBlock> blocks, String name, Object value) {
        if (!(value instanceof String text) || text.isBlank()) return;
        blocks.add(heading(name));
        blocks.add(new DocumentBlock(BlockType.CODE, bounded(text, MAX_LOG_CHARS), List.of(), null));
    }

    private static DocumentBlock heading(String text) {
        return new DocumentBlock(BlockType.HEADING, text, List.of(), null);
    }

    private static DocumentBlock paragraph(String text) {
        return new DocumentBlock(BlockType.PARAGRAPH, text, List.of(), null);
    }

    private static List<DocumentAsset> uniqueAssets(List<DocumentAsset> assets) {
        Map<String, DocumentAsset> byKey = new LinkedHashMap<>();
        for (DocumentAsset asset : assets) byKey.putIfAbsent(asset.objectKey(), asset);
        return List.copyOf(byKey.values());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        return 1;
    }

    private static String fileName(String value) {
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.isBlank() ? "artifact.bin" : name;
    }

    private static String bounded(String value, int limit) {
        if (value.length() <= limit) return value;
        return value.substring(0, limit) + "\n[truncated]";
    }
}
