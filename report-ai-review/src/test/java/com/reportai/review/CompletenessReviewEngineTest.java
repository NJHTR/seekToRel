package com.reportai.review;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.document.model.Placeholder;
import com.reportai.document.model.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompletenessReviewEngineTest {
    private static Document document(List<DocumentBlock> blocks, List<Placeholder> placeholders, List<Question> questions) {
        return new Document("d1", TenantId.of("t1"), "reports/d1.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                blocks, placeholders, questions);
    }

    @Test
    void rejectsMissingTitleAndUnresolvedRequiredPlaceholder() {
        ReviewResult result = new CompletenessReviewEngine().review(document(
                List.of(new DocumentBlock(BlockType.PARAGRAPH, "{{name}}", List.of(), null)),
                List.of(new Placeholder("name", "Name", true)), List.of()));
        assertFalse(result.passed());
        assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("TITLE_MISSING")));
        assertTrue(result.findings().stream().anyMatch(f -> f.ruleId().equals("REQUIRED_PLACEHOLDER_UNRESOLVED")));
    }

    @Test
    void passesCompleteDocumentWithOnlyInformationalWarnings() {
        ReviewResult result = new CompletenessReviewEngine().review(document(
                List.of(new DocumentBlock(BlockType.TITLE, "实验报告", List.of(), null),
                        new DocumentBlock(BlockType.PARAGRAPH, "实验内容", List.of(), null),
                        new DocumentBlock(BlockType.HEADING, "总结", List.of(), null)),
                List.of(), List.of(new Question("q1", "请说明原因？", true))));
        assertTrue(result.passed());
        assertTrue(result.findings().stream().anyMatch(f -> f.severity() == ReviewFinding.Severity.WARNING));
    }
}
