package com.reportai.llm.prompt;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PromptTemplate(String id, int version, String content, boolean active) {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_.-]{0,127})\\s*}}" );

    public PromptTemplate {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("prompt id is invalid");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("prompt version must be positive");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("prompt content must not be blank");
        }
        if (content.length() > 1_000_000) {
            throw new IllegalArgumentException("prompt content is too long");
        }
        id = id.trim();
        content = content;
    }

    public Set<String> variables() {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return Set.copyOf(result);
    }

    public static Pattern variablePattern() {
        return VARIABLE;
    }
}
