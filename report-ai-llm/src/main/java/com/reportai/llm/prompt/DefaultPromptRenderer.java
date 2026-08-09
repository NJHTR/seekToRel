package com.reportai.llm.prompt;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

public final class DefaultPromptRenderer implements PromptRenderer {
    private static final int MAX_RENDERED_LENGTH = 2_000_000;
    private final PromptRepository repository;

    public DefaultPromptRenderer() {
        this(null);
    }

    public DefaultPromptRenderer(PromptRepository repository) {
        this.repository = repository;
    }

    @Override
    public String render(PromptTemplate template, Map<String, ?> variables) {
        Objects.requireNonNull(template, "template must not be null");
        Map<String, ?> values = variables == null ? Map.of() : Map.copyOf(variables);
        for (String variable : template.variables()) {
            if (!values.containsKey(variable) || values.get(variable) == null) {
                throw new PromptRenderException("missing prompt variable: " + variable);
            }
        }
        Matcher matcher = PromptTemplate.variablePattern().matcher(template.content());
        StringBuffer rendered = new StringBuffer(template.content().length());
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            String replacement = Matcher.quoteReplacement(String.valueOf(value));
            matcher.appendReplacement(rendered, replacement);
            if (rendered.length() > MAX_RENDERED_LENGTH) {
                throw new PromptRenderException("rendered prompt exceeds the maximum allowed length");
            }
        }
        matcher.appendTail(rendered);
        if (rendered.length() > MAX_RENDERED_LENGTH) {
            throw new PromptRenderException("rendered prompt exceeds the maximum allowed length");
        }
        return rendered.toString();
    }

    @Override
    public String render(String promptId, Map<String, ?> variables) {
        if (repository == null) {
            throw new PromptRenderException("prompt repository is not configured");
        }
        PromptTemplate template = repository.findActive(promptId)
                .orElseThrow(() -> new PromptRenderException("active prompt not found: " + promptId));
        return render(template, variables);
    }
}
