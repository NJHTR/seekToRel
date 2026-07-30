package com.reportai.review;

import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.document.model.Placeholder;
import com.reportai.document.model.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Performs the inexpensive, deterministic part of release review.  It deliberately
 * does not make an LLM judgement and therefore remains safe to run on retries.
 */
public final class CompletenessReviewEngine implements ReviewEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{\\s*([A-Za-z0-9_.-]{1,128})\\s*}}"
    );

    @Override
    public ReviewResult review(Document document) {
        Objects.requireNonNull(document, "document");
        List<ReviewFinding> findings = new ArrayList<>();
        List<DocumentBlock> blocks = document.blocks();
        reviewTitle(blocks, findings);
        reviewBlocks(blocks, findings);
        reviewPlaceholders(document, blocks, findings);
        reviewQuestions(document.questions(), findings);
        reviewExpectedSections(blocks, findings);
        boolean passed = findings.stream().noneMatch(f -> f.severity() == ReviewFinding.Severity.ERROR);
        return new ReviewResult(passed, findings);
    }

    private static void reviewTitle(List<DocumentBlock> blocks, List<ReviewFinding> findings) {
        boolean title = blocks.stream().anyMatch(b -> b != null && b.type() == BlockType.TITLE && nonBlank(b.text()));
        if (!title) {
            findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "TITLE_MISSING",
                    "document title is missing", "title"));
        }
    }

    private static void reviewBlocks(List<DocumentBlock> blocks, List<ReviewFinding> findings) {
        boolean substantive = false;
        for (int i = 0; i < blocks.size(); i++) {
            DocumentBlock block = blocks.get(i);
            if (block == null || block.type() == null) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "BLOCK_INVALID",
                        "document contains an invalid block", "blocks[" + i + "]"));
                continue;
            }
            if (block.type() == BlockType.IMAGE) {
                if (!nonBlank(block.assetKey())) {
                    findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "IMAGE_ASSET_MISSING",
                            "image block has no asset", "blocks[" + i + "]"));
                } else {
                    substantive = true;
                }
                continue;
            }
            if (block.type() == BlockType.PAGE_BREAK) {
                continue;
            }
            String value = block.text();
            if (block.type() == BlockType.TABLE && !nonBlank(value)
                    && (block.children() == null || block.children().stream().noneMatch(CompletenessReviewEngine::nonBlank))) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "BLOCK_EMPTY",
                        "table block is empty", "blocks[" + i + "]"));
                continue;
            }
            if (!nonBlank(value)) {
                String rule = block.type() == BlockType.CODE ? "CODE_BLOCK_EMPTY" : "BLOCK_EMPTY";
                findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, rule,
                        "content block is empty", "blocks[" + i + "]"));
            } else {
                substantive = true;
            }
        }
        if (!substantive) {
            findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "CONTENT_MISSING",
                    "document has no substantive content", "document"));
        }
    }

    private static void reviewPlaceholders(Document document, List<DocumentBlock> blocks,
                                           List<ReviewFinding> findings) {
        StringBuilder textBuilder = new StringBuilder();
        for (DocumentBlock block : blocks) {
            if (block == null) {
                continue;
            }
            if (block.text() != null) {
                textBuilder.append('\n').append(block.text());
            }
            if (block.children() != null) {
                for (String child : block.children()) {
                    if (child != null) {
                        textBuilder.append('\n').append(child);
                    }
                }
            }
        }
        String allText = textBuilder.toString();
        for (Placeholder placeholder : document.placeholders()) {
            if (placeholder == null || !placeholder.required() || !nonBlank(placeholder.key())) {
                continue;
            }
            var matcher = PLACEHOLDER.matcher(allText);
            boolean unresolved = false;
            while (matcher.find()) {
                if (placeholder.key().equals(matcher.group(1))) {
                    unresolved = true;
                    break;
                }
            }
            if (unresolved) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "REQUIRED_PLACEHOLDER_UNRESOLVED",
                        "required placeholder is unresolved", "placeholder"));
            }
        }
    }

    private static void reviewQuestions(List<Question> questions, List<ReviewFinding> findings) {
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            if (question == null || !question.required()) {
                continue;
            }
            if (!nonBlank(question.text())) {
                findings.add(new ReviewFinding(ReviewFinding.Severity.ERROR, "REQUIRED_QUESTION_EMPTY",
                        "required question has no text", "questions[" + i + "]"));
            } else {
                findings.add(new ReviewFinding(ReviewFinding.Severity.WARNING, "QUESTION_REVIEW_REQUIRED",
                        "question answer requires semantic review", "question:" + question.id()));
            }
        }
    }

    private static void reviewExpectedSections(List<DocumentBlock> blocks, List<ReviewFinding> findings) {
        StringBuilder textBuilder = new StringBuilder();
        for (DocumentBlock block : blocks) {
            if (block != null && nonBlank(block.text())) {
                textBuilder.append(' ').append(block.text());
            }
        }
        String text = textBuilder.toString().toLowerCase();
        if (!text.contains("\u603b\u7ed3") && !text.contains("\u7ed3\u8bba")
                && !text.contains("summary") && !text.contains("conclusion")) {
            findings.add(new ReviewFinding(ReviewFinding.Severity.WARNING, "SUMMARY_NOT_DETECTED",
                    "summary or conclusion section was not detected", "document"));
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
