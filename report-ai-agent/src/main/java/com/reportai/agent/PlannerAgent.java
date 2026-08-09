package com.reportai.agent;

/**
 * Turns the canonical document (or an upstream document analysis) into a bounded
 * execution plan.  Planning is deliberately side-effect free; all execution is left
 * to later tool-backed agents.
 */
public final class PlannerAgent implements Agent {
    public static final String TYPE = "planner";
    public static final String DEFAULT_INPUT_KEY = "document";
    public static final String DEFAULT_OUTPUT_KEY = "plan";
    private static final String DEFAULT_PROMPT = """
            You are ReportAI's Planner Agent. Analyze the supplied canonical document and
            produce a concise, ordered execution plan. Identify required code language,
            files, charts, questions, and document sections. Do not execute code, call
            tools, invent completed outputs, or return Markdown fences. Include explicit
            validation checkpoints and keep the plan deterministic and reproducible.
            When image attachments are supplied, inspect their visible content with
            multimodal understanding; preserve uncertain text as an explicit question
            instead of silently inventing it.
            The canonical document and its parsed blocks are authoritative; the upload
            filename and object key are metadata, never host filesystem paths. If
            sourceDocumentInput is supplied, a sandbox program may read the source DOCX
            only at the exact read-only path `/workspace/input/source-document.docx`.
            Never reconstruct a local Windows/macOS/Linux path, search for the original
            filename, or use sourceObjectKey as a filesystem path. Task inputs and
            embedded assets may be read only at their supplied relativePath values under
            `/workspace/input`; do not add an undeclared input because a file is merely
            mentioned in the document.
            """;

    private final String inputKey;
    private final String outputKey;
    private final String systemPrompt;

    public PlannerAgent() {
        this(DEFAULT_INPUT_KEY, DEFAULT_OUTPUT_KEY, DEFAULT_PROMPT);
    }

    public PlannerAgent(String inputKey, String outputKey, String systemPrompt) {
        this.inputKey = require(inputKey, "inputKey");
        this.outputKey = require(outputKey, "outputKey");
        this.systemPrompt = requirePrompt(systemPrompt);
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
        Object document = context.state().get(inputKey);
        if (document == null) {
            return LlmAgentSupport.missingContext("required state is missing: " + inputKey);
        }
        Object inputs = context.state().get("taskInputs");
        Object documentAssets = context.state().get("documentAssets");
        Object sourceDocumentInput = context.state().get("sourceDocumentInput");
        Object ocrText = context.state().get("documentAssetOcrText");
        String promptInput = LlmAgentSupport.render(document);
        if (inputs != null) {
            promptInput += "\nprovidedTaskInputs=" + LlmAgentSupport.render(inputs)
                    + "\nThese inputs are mounted read-only under /workspace/input; do not invent substitutes.";
        }
        if (documentAssets != null) {
            promptInput += "\nprovidedDocumentAssets=" + LlmAgentSupport.render(documentAssets)
                    + "\nEmbedded images are mounted read-only under /workspace/input/document-assets and are attached to this request when vision is available. "
                    + "Read/transcribe visible text; do not infer illegible pixels.";
        }
        if (sourceDocumentInput != null) {
            promptInput += "\nprovidedSourceDocumentInput=" + LlmAgentSupport.render(sourceDocumentInput)
                    + "\nThe source DOCX is available to generated code only as the fixed read-only path "
                    + "/workspace/input/source-document.docx. Never use originalFileName, sourceObjectKey, "
                    + "a host filesystem path, or filename discovery in generated code.";
        } else {
            promptInput += "\nsourceDocumentInput is not available to generated code; use the canonical document state and "
                    + "declared task inputs only. Never search the host filesystem for the uploaded DOCX.";
        }
        if (ocrText != null) {
            promptInput += "\nvalidatedOcrFallback=" + LlmAgentSupport.render(ocrText)
                    + "\nUse this transcription only as evidence for text that was not available through direct vision; preserve uncertainty and do not invent missing characters.";
        }
        return LlmAgentSupport.call(context, TYPE, systemPrompt, promptInput, outputKey);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }

    private static String requirePrompt(String value) {
        if (value == null || value.isBlank() || value.length() > LlmAgentSupport.MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("systemPrompt is invalid");
        }
        return value.trim();
    }
}
