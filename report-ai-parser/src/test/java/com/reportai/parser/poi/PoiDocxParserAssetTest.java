package com.reportai.parser.poi;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.parser.ParserRequest;
import com.reportai.parser.ParserResult;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoiDocxParserAssetTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void extractsImageBytesAndSecureObjectReference() throws Exception {
        byte[] docx = createDocx(PNG, Document.PICTURE_TYPE_PNG, "../plot.png");
        ParserResult result = new PoiDocxParser().parse(new ParserRequest(
                new ByteArrayInputStream(docx), "report.docx", "documents/source.docx",
                TenantId.of("tenant-a"), PoiDocxParser.DOCX_MEDIA_TYPE, docx.length, "doc-1"));

        assertEquals(1, result.assets().size());
        var asset = result.assets().get(0);
        assertArrayEquals(PNG, asset.content());
        assertEquals("image/png", asset.metadata().mediaType());
        assertEquals(PNG.length, asset.metadata().size());
        assertTrue(asset.metadata().objectKey().matches("documents/[0-9a-f]{32}/[0-9a-f]{32}/assets/1-.+"));
        assertEquals(BlockType.IMAGE, result.document().blocks().get(result.document().blocks().size() - 1).type());
        assertEquals(asset.metadata().objectKey(),
                result.document().blocks().get(result.document().blocks().size() - 1).assetKey());
        assertEquals(asset.metadata(), result.document().assets().get(0));
    }

    @Test
    void rejectsMismatchedImageSignatureWithoutPersistableAsset() throws Exception {
        byte[] docx = createDocx(PNG, Document.PICTURE_TYPE_JPEG, "plot.jpg");
        ParserResult result = new PoiDocxParser().parse(new ParserRequest(
                new ByteArrayInputStream(docx), "report.docx", "documents/source.docx",
                TenantId.of("tenant-a"), PoiDocxParser.DOCX_MEDIA_TYPE, docx.length, "doc-2"));

        assertTrue(result.assets().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> "IMAGE_TYPE_REJECTED".equals(w.code())));
        assertNotNull(result.document().blocks().get(result.document().blocks().size() - 1));
        assertEquals(BlockType.IMAGE,
                result.document().blocks().get(result.document().blocks().size() - 1).type());
    }

    private static byte[] createDocx(byte[] image, int pictureType, String fileName) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFRun run = document.createParagraph().createRun();
            run.addPicture(new ByteArrayInputStream(image), pictureType, fileName,
                    Units.toEMU(10), Units.toEMU(10));
            document.write(output);
            return output.toByteArray();
        }
    }
}
