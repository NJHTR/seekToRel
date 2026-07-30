package com.reportai.word;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.review.CompletenessReviewEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWordReleaseServiceTest {
    private static Document document(String title) {
        return new Document("d1", TenantId.of("t1"), "reports/d1.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, title, List.of(), null),
                        new DocumentBlock(BlockType.PARAGRAPH, "内容", List.of(), null),
                        new DocumentBlock(BlockType.HEADING, "总结", List.of(), null)), List.of(), List.of());
    }

    @Test
    void doesNotWriteOutputWhenReviewRejects() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ReleaseResult result = new DefaultWordReleaseService(new CompletenessReviewEngine(), new PoiWordBuilder())
                .release(document(""), output);
        assertEquals(ReleaseStatus.REJECTED, result.status());
        assertNull(result.export());
        assertEquals(0, output.size());
    }

    @Test
    void exportsAfterReviewPasses() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ReleaseResult result = new DefaultWordReleaseService(new CompletenessReviewEngine(), new PoiWordBuilder())
                .release(document("实验报告"), output);
        assertTrue(result.released());
        assertNotNull(result.export());
        assertTrue(output.size() > 0);
    }
}
