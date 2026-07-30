package com.reportai.word;

import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import org.apache.poi.util.Units;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Apache POI implementation of the canonical document-to-DOCX port. */
public final class PoiWordBuilder implements WordBuilder {
    @Override
    public ExportResult build(Document document, OutputStream output, WordBuildOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(options, "options");
        List<ExportWarning> warnings = new ArrayList<>();
        int blocksWritten = 0;
        int assetsWritten = 0;
        try (XWPFDocument word = new XWPFDocument()) {
            for (int index = 0; index < document.blocks().size(); index++) {
                DocumentBlock block = document.blocks().get(index);
                if (block == null || block.type() == null) {
                    warnings.add(new ExportWarning("BLOCK_INVALID", "block was skipped", index));
                    continue;
                }
                boolean written = writeBlock(word, block, index, options, warnings);
                if (written) {
                    blocksWritten++;
                    if (block.type() == BlockType.IMAGE) {
                        assetsWritten++;
                    }
                }
            }
            word.write(output);
            output.flush();
            return new ExportResult(blocksWritten, assetsWritten, warnings);
        } catch (IOException failure) {
            throw new WordExportException("DOCX export failed", failure);
        } catch (RuntimeException failure) {
            if (failure instanceof WordExportException) {
                throw failure;
            }
            throw new WordExportException("DOCX export failed", failure);
        }
    }

    private static boolean writeBlock(XWPFDocument word, DocumentBlock block, int index,
                                      WordBuildOptions options, List<ExportWarning> warnings) {
        String text = block.text() == null ? "" : block.text();
        switch (block.type()) {
            case TITLE -> {
                XWPFParagraph paragraph = word.createParagraph();
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = paragraph.createRun();
                run.setBold(true);
                run.setFontSize(18);
                run.setText(text);
                return true;
            }
            case HEADING -> {
                XWPFParagraph paragraph = word.createParagraph();
                paragraph.setStyle("Heading1");
                paragraph.createRun().setText(text);
                return true;
            }
            case PARAGRAPH -> {
                word.createParagraph().createRun().setText(text);
                return true;
            }
            case CODE -> {
                XWPFParagraph paragraph = word.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setFontFamily("Consolas");
                run.setFontSize(10);
                run.setText(text);
                return true;
            }
            case TABLE -> {
                writeTable(word, block);
                return true;
            }
            case PAGE_BREAK -> {
                word.createParagraph().createRun().addBreak(BreakType.PAGE);
                return true;
            }
            case IMAGE -> {
                return writeImage(word, block, index, options, warnings);
            }
            default -> {
                warnings.add(new ExportWarning("BLOCK_UNSUPPORTED", "unsupported block was skipped", index));
                return false;
            }
        }
    }

    private static void writeTable(XWPFDocument word, DocumentBlock block) {
        List<String> rows = block.children() == null || block.children().isEmpty()
                ? List.of(block.text() == null ? "" : block.text()) : block.children();
        int columns = rows.stream().mapToInt(row -> row.split("\\s+\\|\\s+", -1).length).max().orElse(1);
        XWPFTable table = word.createTable(Math.max(1, rows.size()), columns);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            String[] cells = rows.get(rowIndex).split("\\s+\\|\\s+", -1);
            for (int col = 0; col < columns; col++) {
                XWPFTableCell cell = row.getCell(col);
                cell.removeParagraph(0);
                cell.addParagraph().createRun().setText(col < cells.length ? cells[col].trim() : "");
            }
        }
    }

    private static boolean writeImage(XWPFDocument word, DocumentBlock block, int index,
                                      WordBuildOptions options, List<ExportWarning> warnings) {
        String key = block.assetKey();
        validateAssetKey(key);
        Optional<AssetResolver.Asset> resolved;
        try {
            resolved = options.assetResolver().resolve(key);
        } catch (IOException failure) {
            warnings.add(new ExportWarning("ASSET_UNAVAILABLE", "image asset is unavailable", index));
            return false;
        }
        if (resolved.isEmpty()) {
            warnings.add(new ExportWarning("ASSET_MISSING", "image asset is missing", index));
            return false;
        }
        AssetResolver.Asset asset = resolved.get();
        try (InputStream input = asset.content()) {
            if (asset.declaredSize() > options.maxAssetBytes()) {
                warnings.add(new ExportWarning("ASSET_TOO_LARGE", "image asset exceeds export limit", index));
                return false;
            }
            byte[] bytes;
            try {
                bytes = readBounded(input, options.maxAssetBytes());
            } catch (AssetTooLargeException tooLarge) {
                warnings.add(new ExportWarning("ASSET_TOO_LARGE", "image asset exceeds export limit", index));
                return false;
            }
            String mediaType = asset.mediaType().toLowerCase(Locale.ROOT).trim();
            int pictureType = pictureType(mediaType, bytes);
            if (pictureType < 0 || !options.allowedImageMediaTypes().contains(mediaType)) {
                warnings.add(new ExportWarning("ASSET_TYPE_REJECTED", "image media type is not allowed", index));
                return false;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() > options.maxImageWidth()
                    || image.getHeight() > options.maxImageHeight()) {
                warnings.add(new ExportWarning("ASSET_DIMENSIONS_REJECTED", "image dimensions are not allowed", index));
                return false;
            }
            XWPFParagraph paragraph = word.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            paragraph.createRun().addPicture(new ByteArrayInputStream(bytes), pictureType,
                    safeFileName(mediaType), Units.toEMU(Math.min(image.getWidth(), options.maxImageWidth())),
                    Units.toEMU(Math.min(image.getHeight(), options.maxImageHeight())));
            return true;
        } catch (IOException | InvalidFormatException failure) {
            warnings.add(new ExportWarning("ASSET_INVALID", "image asset could not be read", index));
            return false;
        }
    }

    private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new AssetTooLargeException();
            }
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private static int pictureType(String mediaType, byte[] bytes) {
        boolean png = mediaType.equals("image/png") && bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        boolean jpeg = mediaType.equals("image/jpeg") && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        boolean gif = mediaType.equals("image/gif") && bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
        if (png) return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
        if (jpeg) return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
        if (gif) return org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF;
        return -1;
    }

    private static String safeFileName(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> "image.png";
            case "image/jpeg" -> "image.jpg";
            default -> "image.gif";
        };
    }

    private static void validateAssetKey(String key) {
        if (key == null || key.isBlank() || key.indexOf('\0') >= 0 || key.indexOf('\\') >= 0
                || key.startsWith("/") || key.matches("[A-Za-z]:.*")) {
            throw new WordExportException("unsafe asset reference");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new WordExportException("unsafe asset reference");
            }
        }
    }

    private static final class AssetTooLargeException extends IOException {
        private AssetTooLargeException() {
            super("asset exceeds configured limit");
        }
    }
}
