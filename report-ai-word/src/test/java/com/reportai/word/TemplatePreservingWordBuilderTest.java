package com.reportai.word;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TemplatePreservingWordBuilderTest {
    @Test
    void keepsTemplateTableAndInsertsAnswerAfterAnchor() throws Exception {
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        try (XWPFDocument source = new XWPFDocument()) {
            source.createParagraph().createRun().setText("实验报告");
            XWPFTable table = source.createTable(1, 1);
            table.getCTTbl().addNewTblGrid().addNewGridCol().setW(java.math.BigInteger.valueOf(9000));
            table.getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("问题一：说明测试范围");
            source.write(sourceBytes);
        }
        Document document = new Document("doc", TenantId.of("tenant"), "documents/source.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", List.of(), List.of(), List.of());
        AssetResolver sourceResolver = key -> Optional.of(new AssetResolver.Asset(document.mediaType(),
                new ByteArrayInputStream(sourceBytes.toByteArray()), sourceBytes.size()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WordBuildOptions options = new WordBuildOptions(1024 * 1024, 4096, 4096, null,
                AssetResolver.none(), sourceResolver,
                List.of(new TemplatePatch("问题一：说明测试范围", "答案紧跟在问题后面。")));

        new TemplatePreservingWordBuilder(new PoiWordBuilder()).build(document, output, options);

        try (XWPFDocument exported = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(1, exported.getTables().size());
            String text = exported.getTables().get(0).getRow(0).getCell(0).getText();
            assertTrue(text.contains("问题一：说明测试范围"));
            assertTrue(text.contains("答案紧跟在问题后面。"));
            assertNotNull(exported.getTables().get(0).getCTTbl().getTblGrid());
        }
    }
}
