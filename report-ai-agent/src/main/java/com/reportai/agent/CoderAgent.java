package com.reportai.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces source code from a planner output.  It never writes files or invokes a
 * compiler; those side effects are delegated to {@link RuntimeAgent} and its Tool.
 */
public final class CoderAgent implements Agent {
    public static final String TYPE = "coder";
    public static final String DEFAULT_PLAN_KEY = "plan";
    public static final String DEFAULT_LANGUAGE_KEY = "language";
    public static final String DEFAULT_OUTPUT_KEY = "code";
    private static final String DEFAULT_PROMPT = """
            You are ReportAI's Coder Agent. Generate complete, deterministic source code
            that satisfies the supplied execution plan. Keep the implementation compact
            (prefer a small runnable program over scaffolding) so generation completes
            within a bounded provider request. Respect the requested language,
            avoid network access and host-specific paths, and include all imports and a
            runnable entry point. The sandbox invokes the entry point without extra CLI
            arguments, so the program must run its default end-to-end path as
            `python main.py` (or the equivalent language command). Do not assume datasets,
            configuration files, or third-party packages that are not supplied in the
            runtime capabilities. For the scientific profile, Torch/TorchVision
            experiments must never substitute synthetic fixtures for a required real
            dataset; emit a clear bounded prerequisite failure when the input is absent.
            When the document contains attached images, use the supplied multimodal
            evidence and the read-only files under `/workspace/input/document-assets`;
            do not report OCR as missing merely because the sandbox has no vision library.
            When editing a DOCX with Python ElementTree, remember that a paragraph
            found with `body.iter(...)` may be nested inside a table cell. Never use
            `list(body).index(anchor)` for a descendant. Resolve the anchor's actual
            parent first (for example with a recursive `find_parent(root, anchor)`)
            and insert beside the anchor in that parent; a valid helper scans
            `for candidate in root.iter(): if anchor in list(candidate): return candidate`.
            Preserve table structure and
            fail with a clear prerequisite message if the requested section cannot be
            located. This rule applies to every XML mutation, not only headings.
            The canonical document text and parsed blocks are already supplied to you;
            the upload's original filename and object key are metadata only. If
            `sourceDocumentInput` is present, the source DOCX is mounted read-only at
            exactly `/workspace/input/source-document.docx`. Use that fixed path only
            when the experiment truly needs to parse the DOCX. Never use the original
            filename, `sourceObjectKey`, a Windows/macOS/Linux host path, cwd discovery,
            or globbing to locate the upload. Task inputs and embedded assets may be
            opened only through their declared relative paths under `/workspace/input`.
            Do not declare a missing prerequisite for the source DOCX when the canonical
            text/assets are sufficient, and do not require an undeclared dataset merely
            because the report mentions one as an example.
            For experiments that genuinely do not require external data, deterministic
            fixtures are acceptable only when the plan explicitly permits them. Return
            source code only, without Markdown fences or commentary. Never claim that
            code was executed.
            """;
    /**
     * Coder is allowed to explain that a scientific experiment cannot run yet,
     * but that declaration is a deterministic preflight result, not executable
     * work.  Detect the small JSON contract the prompt asks for before the
     * source reaches RuntimeAgent; otherwise a deliberate exit(2) would be
     * misreported as a generic Python runtime failure.
     */
    private static final Pattern PREREQUISITE_FAILURE = Pattern.compile(
            "(?is)(?:[\\\"']status[\\\"']\\s*[:=]\\s*[\\\"']prerequisite_failure[\\\"']"
                    + "|[\\\"']missing_prerequisites[\\\"']\\s*[:=]"
                    + "|REQUIRED_CONFIRMATIONS"
                    + "|\\bPrerequisiteFailure\\b)");
    /**
     * A model sometimes copies the developer's local DOCX path into generated
     * source even after receiving the sandbox contract.  Host paths are never
     * valid inside the container.  When the immutable source input is present,
     * rewrite only quoted absolute paths ending in {@code .docx} to the single
     * mounted path.  This is deterministic, language-neutral, and leaves relative
     * dataset paths untouched.
     */
    private static final Pattern HOST_DOCX_PATH = Pattern.compile(
            "(?i)(['\"])(?:[A-Za-z]:[\\\\/]|\\\\\\\\|/(?:Users|home|mnt|Volumes)/)[^'\"\\r\\n]*?\\.docx\\1");
    private static final String FIXED_SOURCE_DOCUMENT_PATH = "/workspace/input/source-document.docx";

    private final String planKey;
    private final String languageKey;
    private final String outputKey;
    private final String systemPrompt;

    public CoderAgent() {
        this(DEFAULT_PLAN_KEY, DEFAULT_LANGUAGE_KEY, DEFAULT_OUTPUT_KEY, DEFAULT_PROMPT);
    }

    public CoderAgent(String planKey, String languageKey, String outputKey, String systemPrompt) {
        this.planKey = require(planKey, "planKey");
        this.languageKey = require(languageKey, "languageKey");
        this.outputKey = require(outputKey, "outputKey");
        if (systemPrompt == null || systemPrompt.isBlank() || systemPrompt.length() > LlmAgentSupport.MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("systemPrompt is invalid");
        }
        this.systemPrompt = systemPrompt.trim();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null) {
            return LlmAgentSupport.missingContext("agent context is required");
        }
        Object plan = context.state().get(planKey);
        if (plan == null) {
            return LlmAgentSupport.missingContext("required state is missing: " + planKey);
        }
        Object language = context.state().get(languageKey);
        String languageText = language == null ? "unspecified" : LlmAgentSupport.render(language);
        Object capabilities = context.state().get("runtimeCapabilities");
        Object environment = context.state().get("environmentManifest");
        Object inputs = context.state().get("taskInputs");
        Object documentAssets = context.state().get("documentAssets");
        Object sourceDocumentInput = context.state().get("sourceDocumentInput");
        Object ocrText = context.state().get("documentAssetOcrText");
        Object runtimeFailureFeedback = context.state().get("runtimeFailureFeedback");
        String input = "language=" + languageText + "\nplan=" + LlmAgentSupport.render(plan)
                + (capabilities == null ? "" : "\nruntimeCapabilities=" + LlmAgentSupport.render(capabilities))
                + (environment == null ? "" : "\nresolvedEnvironment=" + LlmAgentSupport.render(environment))
                + (inputs == null ? "" : "\nprovidedTaskInputs=" + LlmAgentSupport.render(inputs)
                + "\nUse only these read-only files under /workspace/input; never synthesize replacements.")
                + (documentAssets == null ? "" : "\nprovidedDocumentAssets=" + LlmAgentSupport.render(documentAssets)
                + "\nEmbedded images are available read-only under /workspace/input/document-assets and may be inspected with the supplied vision transcription. Do not invent text that is not legible.")
                + (sourceDocumentInput == null
                ? "\nsourceDocumentInput is not available to generated code; use canonical document state and declared task inputs only. Never search the host filesystem for the uploaded DOCX."
                : "\nprovidedSourceDocumentInput=" + LlmAgentSupport.render(sourceDocumentInput)
                + "\nIf DOCX parsing is genuinely required, open only /workspace/input/source-document.docx; never use originalFileName, sourceObjectKey, a host path, cwd discovery, or filename globbing.")
                + (ocrText == null ? "" : "\nvalidatedOcrFallback=" + LlmAgentSupport.render(ocrText)
                + "\nUse the OCR text as untrusted evidence only; do not fabricate values that are absent or uncertain.")
                + (runtimeFailureFeedback == null ? "" : "\npreviousRuntimeFailure="
                + LlmAgentSupport.render(runtimeFailureFeedback)
                + "\nRepair the generated program using this bounded sandbox diagnostic. "
                + "Preserve the resolved dependency and input contract unless the diagnostic proves it is invalid; "
                + "return the complete corrected source only and do not claim that it was executed.");
        AgentResult result = LlmAgentSupport.call(context, TYPE, systemPrompt, input, outputKey);
        if (result.status() != AgentResult.Status.COMPLETED) {
            return result;
        }
        String source;
        try {
            source = normalizeCode(String.valueOf(result.outputs().get(outputKey)));
        } catch (IllegalArgumentException invalid) {
            return LlmAgentSupport.missingContext("generated code is invalid");
        }
        Map<String, Object> outputs = new LinkedHashMap<>(result.outputs());
        String normalizedSource = normalizeHostSourcePath(source, sourceDocumentInput);
        if (!normalizedSource.equals(source)) {
            source = normalizedSource;
            outputs.put("sourceDocumentPathNormalized", true);
        }
        outputs.put(outputKey, source);
        outputs.put("codeLanguage", languageText);
        if (PREREQUISITE_FAILURE.matcher(source).find()) {
            outputs.put("toolErrorCode", "PYTHON_INPUT_MISSING");
            outputs.put("prerequisiteFailure", true);
            return new AgentResult(AgentResult.Status.FAILED, outputs,
                    "PYTHON_INPUT_MISSING: generated code declares unmet real-input prerequisites");
        }
        return new AgentResult(AgentResult.Status.COMPLETED, outputs, result.message());
    }

    private static String normalizeCode(String value) {
        String code = LlmAgentSupport.bound(value, LlmAgentSupport.MAX_OUTPUT_CHARS, "generated code").trim();
        if (code.startsWith("```") && code.endsWith("```")) {
            int firstLine = code.indexOf('\n');
            if (firstLine > 0) {
                code = code.substring(firstLine + 1, code.length() - 3).trim();
            }
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("generated code must not be blank");
        }
        return code;
    }

    private static String normalizeHostSourcePath(String source, Object sourceDocumentInput) {
        if (sourceDocumentInput == null || source == null || source.isBlank()) {
            return source;
        }
        Matcher matcher = HOST_DOCX_PATH.matcher(source);
        StringBuffer normalized = new StringBuffer(source.length());
        while (matcher.find()) {
            String quote = matcher.group(1);
            matcher.appendReplacement(normalized,
                    Matcher.quoteReplacement(quote + FIXED_SOURCE_DOCUMENT_PATH + quote));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
