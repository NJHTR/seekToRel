package com.reportai.word;

import com.reportai.document.model.Document;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.xmlbeans.XmlCursor;

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

/**
 * Applies small, anchored edits to the uploaded DOCX instead of reconstructing
 * it from the lossy canonical block model. The original styles, table XML,
 * relationships and embedded media therefore remain authoritative.
 */
public final class TemplatePreservingWordBuilder implements WordBuilder {
    private final WordBuilder fallback;

    public TemplatePreservingWordBuilder(WordBuilder fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public ExportResult build(Document document, OutputStream output, WordBuildOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(options, "options");
        Optional<AssetResolver.Asset> source;
        try {
            source = options.sourceDocumentResolver().resolve(document.sourceObjectKey());
        } catch (IOException failure) {
            source = Optional.empty();
        }
        if (source.isEmpty()) {
            return fallback.build(document, output, options);
        }
        try (InputStream input = source.get().content(); XWPFDocument word = new XWPFDocument(input)) {
            for (TemplatePatch patch : options.templatePatches()) {
                if (!applyPatch(word, patch, options) && !patch.answer().isBlank()) {
                    throw new WordExportException("template anchor not found: " + patch.anchor());
                }
            }
            word.write(output);
            output.flush();
            return new ExportResult(options.templatePatches().size(),
                    (int) options.templatePatches().stream().flatMap(p -> p.figureAssetKeys().stream()).count(),
                    List.of());
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof WordExportException) throw (WordExportException) failure;
            throw new WordExportException("template-preserving DOCX export failed", failure);
        }
    }

    private static boolean applyPatch(XWPFDocument word, TemplatePatch patch, WordBuildOptions options) {
        for (IBodyElement element : word.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph && matches(paragraph.getText(), patch.anchor())) {
                insertAfter(paragraph, patch, options);
                return true;
            }
            if (element instanceof XWPFTable table && applyTablePatch(table, patch, options)) {
                return true;
            }
        }
        return false;
    }

    private static boolean applyTablePatch(XWPFTable table, TemplatePatch patch, WordBuildOptions options) {
        for (var row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    if (matches(paragraph.getText(), patch.anchor())) {
                        insertAfter(paragraph, patch, options);
                        return true;
                    }
                }
                for (XWPFTable nested : cell.getTables()) {
                    if (applyTablePatch(nested, patch, options)) return true;
                }
            }
        }
        return false;
    }

    private static boolean matches(String text, String anchor) {
        return normalize(text).contains(normalize(anchor));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", "").trim();
    }

    private static void insertAfter(XWPFParagraph paragraph, TemplatePatch patch, WordBuildOptions options) {
        if (!patch.answer().isBlank()) {
            String original = paragraph.getText() == null ? "" : paragraph.getText();
            String anchor = patch.anchor();
            int start = original.indexOf(anchor);
            if (start >= 0) {
                String before = original.substring(0, start + anchor.length());
                String after = original.substring(start + anchor.length());
                while (!paragraph.getRuns().isEmpty()) paragraph.removeRun(0);
                XWPFRun question = paragraph.createRun();
                question.setText(before);
                XWPFRun answer = paragraph.createRun();
                answer.addBreak();
                appendAnswer(answer, patch.answer());
                if (!after.isBlank()) {
                    XWPFRun trailing = paragraph.createRun();
                    trailing.addBreak();
                    trailing.setText(after);
                }
            } else {
                appendAnswer(paragraph, patch.answer());
            }
        }
        for (String key : patch.figureAssetKeys()) {
            insertImageAfter(paragraph, key, options);
        }
    }

    private static void appendAnswer(XWPFParagraph paragraph, String answer) {
        String clean = answer == null ? "" : answer.replace("```python", "").replace("```", "").strip();
        String[] lines = clean.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) paragraph.createRun().addBreak();
            String line = lines[i].strip();
            XWPFRun run = paragraph.createRun();
            if (line.startsWith("|") && line.endsWith("|")) {
                line = line.substring(1, line.length() - 1).replace('|', ' ');
            }
            run.setText(line);
        }
    }

    private static void appendAnswer(XWPFRun run, String answer) {
        String clean = answer == null ? "" : answer.replace("```python", "").replace("```", "").strip();
        String[] lines = clean.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            String line = lines[i].strip();
            if (line.startsWith("|") && line.endsWith("|")) {
                line = line.substring(1, line.length() - 1).replace('|', ' ');
            }
            run.setText(line);
        }
    }

    private static void insertImageAfter(XWPFParagraph anchor, String key, WordBuildOptions options) {
        if (key == null || key.isBlank()) return;
        try {
            Optional<AssetResolver.Asset> resolved = options.assetResolver().resolve(key);
            if (resolved.isEmpty()) return;
            AssetResolver.Asset asset = resolved.get();
            try (InputStream input = asset.content()) {
                byte[] bytes = readBounded(input, options.maxAssetBytes());
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
                XmlCursor cursor = anchor.getCTP().newCursor();
                try {
                    cursor.toEndToken();
                    cursor.toNextToken();
                    XWPFParagraph imageParagraph = anchor.getBody().insertNewParagraph(cursor);
                    if (imageParagraph == null) return;
                    imageParagraph.setAlignment(ParagraphAlignment.CENTER);
                    imageParagraph.createRun().addPicture(new ByteArrayInputStream(bytes), pictureType(asset.mediaType()),
                            "artifact-image", Units.toEMU(Math.min(image.getWidth(), options.maxImageWidth())),
                            Units.toEMU(Math.min(image.getHeight(), options.maxImageHeight())));
                } finally {
                    cursor.dispose();
                }
            }
        } catch (IOException | InvalidFormatException | RuntimeException ignored) {
            // A missing optional figure must not corrupt the retained template.
        }
    }

    private static int pictureType(String mediaType) {
        return switch (mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
            case "image/jpeg", "image/jpg" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
            case "image/gif" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF;
            default -> throw new IllegalArgumentException("unsupported image type");
        };
    }

    private static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("asset too large");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
