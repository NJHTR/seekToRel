package com.reportai.word;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PoiWordBuilderTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void exportsCanonicalBlocksAndImageWithoutClosingCallerStream() throws Exception {
        String assetKey = "reports/r1#images/1.png";
        Document document = new Document("d1", TenantId.of("t1"), "reports/r1.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "实验报告", List.of(), null),
                        new DocumentBlock(BlockType.PARAGRAPH, "正文", List.of(), null),
                        new DocumentBlock(BlockType.TABLE, "a | b", List.of("a | b"), null),
                        new DocumentBlock(BlockType.CODE, "print(1)", List.of(), null),
                        new DocumentBlock(BlockType.IMAGE, null, List.of(), assetKey)), List.of(), List.of());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExportResult result = new PoiWordBuilder().build(document, output,
                new WordBuildOptions(1024 * 1024, 100, 100, null, FakeAssetResolver.of(assetKey, "image/png", PNG)));
        assertEquals(5, result.blocksWritten());
        assertEquals(1, result.assetsWritten());
        assertTrue(output.size() > 0);
        try (XWPFDocument parsed = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertTrue(parsed.getParagraphs().stream().anyMatch(p -> p.getText().contains("实验报告")));
        }
    }

    @Test
    void missingAssetProducesSafeWarningWithoutLeakingKey() {
        String secretKey = "private/tenant-secret.png";
        Document document = new Document("d1", TenantId.of("t1"), "source.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.IMAGE, null, List.of(), secretKey)), List.of(), List.of());
        ExportResult result = new PoiWordBuilder().build(document, new ByteArrayOutputStream(),
                new WordBuildOptions(1024, 100, 100, null, AssetResolver.none()));
        assertEquals("ASSET_MISSING", result.warnings().get(0).code());
        assertFalse(result.warnings().get(0).message().contains(secretKey));
    }

    @Test
    void pathTraversalIsRejectedBeforeResolver() {
        Document document = new Document("d1", TenantId.of("t1"), "source.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.IMAGE, null, List.of(), "../secret.png")), List.of(), List.of());
        assertThrows(WordExportException.class, () -> new PoiWordBuilder().build(document, new ByteArrayOutputStream(),
                new WordBuildOptions(1024, 100, 100, null, AssetResolver.none())));
    }
}
