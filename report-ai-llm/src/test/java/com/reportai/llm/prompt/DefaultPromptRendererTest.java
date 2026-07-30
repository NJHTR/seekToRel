package com.reportai.llm.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultPromptRendererTest {
    @Test
    void rendersActiveVersionThroughRepository() {
        InMemoryPromptRepository repository = new InMemoryPromptRepository();
        repository.save(new PromptTemplate("planner", 1, "Plan {{ document }} for {{ user }}", false));
        repository.save(new PromptTemplate("planner", 2, "Plan {{ document }} for {{ user }}", true));

        DefaultPromptRenderer renderer = new DefaultPromptRenderer(repository);

        assertEquals("Plan report for alice", renderer.render("planner",
                Map.of("document", "report", "user", "alice")));
    }

    @Test
    void rejectsMissingVariablesBeforeCallingProvider() {
        DefaultPromptRenderer renderer = new DefaultPromptRenderer(
                new InMemoryPromptRepository());
        assertThrows(PromptRenderException.class,
                () -> renderer.render(new PromptTemplate("planner", 1, "Hello {{ name }}", true), Map.of()));
    }
}
