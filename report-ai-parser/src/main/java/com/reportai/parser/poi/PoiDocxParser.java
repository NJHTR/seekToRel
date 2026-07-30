package com.reportai.parser.poi;

import com.reportai.common.id.IdGenerator;
import com.reportai.common.id.UuidIdGenerator;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentAsset;
import com.reportai.document.model.DocumentAssetKeyFactory;
import com.reportai.document.model.DocumentBlock;
import com.reportai.document.model.Placeholder;
import com.reportai.document.model.Question;
import com.reportai.parser.DocumentParseException;
import com.reportai.parser.DocumentParserPort;
import com.reportai.parser.ParserRequest;
import com.reportai.parser.ParserResult;
import com.reportai.parser.ParserWarning;
import com.reportai.parser.asset.ParsedAsset;
import com.reportai.parser.security.ParserLimits;
import com.reportai.parser.security.SecureDocxArchive;
import com.reportai.parser.security.UnsafeDocumentException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Apache POI adapter for OOXML Word documents. */
public final class PoiDocxParser implements DocumentParserPort, com.reportai.document.port.DocumentParser {
    public static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]{1,128})\\s*}}")
            ;
    private static final Pattern QUESTION_MARK = Pattern.compile("[?？]");

    private static final Set<String> SUPPORTED_ASSET_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/bmp", "image/tiff");

    private final ParserLimits limits;
    private final IdGenerator idGenerator;

    public PoiDocxParser() {
        this(ParserLimits.docxDefaults(), new UuidIdGenerator());
    }

    public PoiDocxParser(ParserLimits limits, IdGenerator idGenerator) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public boolean supports(String mediaType, String fileName) {
        return mediaType != null && DOCX_MEDIA_TYPE.equalsIgnoreCase(mediaType.trim())
                && fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    @Override
    public ParserResult parse(ParserRequest request) {
        Objects.requireNonNull(request, "request");
        if (!supports(request.mediaType(), request.fileName())) {
            throw new DocumentParseException("unsupported document type");
        }
        validateFileName(request.fileName());

        SecureDocxArchive.Spool spool = null;
        try {
            spool = SecureDocxArchive.spool(request.input(), request.contentLength(), limits);
            List<ParserWarning> warnings = new ArrayList<>();
            ParseAccumulator accumulator = new ParseAccumulator(request, limits, warnings);
            try (OPCPackage packageHandle = OPCPackage.open(spool.path().toFile(), PackageAccess.READ);
                 XWPFDocument word = new XWPFDocument(packageHandle)) {
                parseBody(word.getBodyElements(), accumulator);
                // Headers and footers are intentionally not merged into body order. Their
                // binaries are still extracted as metadata-only assets to avoid data loss.
                for (XWPFPictureData picture : word.getAllPictures()) {
                    accumulator.extractAsset(picture);
                }
                if (word.getAllPictures().size() > accumulator.referencedImageCount()) {
                    warnings.add(new ParserWarning("UNREFERENCED_IMAGE", "some embedded images were not in body runs"));
                }
            }
            Document document = new Document(
                    request.documentId() == null ? idGenerator.nextId() : request.documentId(),
                    request.tenantId(),
                    request.sourceObjectKey(),
                    DOCX_MEDIA_TYPE,
                    accumulator.blocks(),
                    accumulator.placeholders(),
                    accumulator.questions(),
                    accumulator.assetMetadata());
            return new ParserResult(document, spool.bytes(), warnings, accumulator.assets());
        } catch (UnsafeDocumentException | IllegalArgumentException failure) {
            throw new DocumentParseException("document failed security validation", failure);
        } catch (IOException | InvalidFormatException | RuntimeException failure) {
            throw new DocumentParseException("document could not be parsed", failure);
        } finally {
            if (spool != null) {
                spool.delete();
            }
        }
    }

    /** Compatibility adapter for the document module's stable parser port. */
    @Override
    public Document parse(java.io.InputStream input, String fileName, String sourceObjectKey,
                          com.reportai.common.tenant.TenantId tenantId, String mediaType) {
        return parse(new ParserRequest(input, fileName, sourceObjectKey, tenantId, mediaType, -1)).document();
    }

    private static void validateFileName(String fileName) {
        if (fileName.indexOf('\0') >= 0 || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
                || fileName.contains("..") || fileName.length() > 255) {
            throw new DocumentParseException("invalid document file name");
        }
    }

    private static void parseBody(List<IBodyElement> bodyElements, ParseAccumulator accumulator) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                parseParagraph(paragraph, accumulator);
            } else if (element instanceof XWPFTable table) {
                parseTable(table, accumulator);
            }
        }
    }

    private static void parseParagraph(XWPFParagraph paragraph, ParseAccumulator accumulator) {
        String text = accumulator.sanitize(paragraph.getText());
        String style = paragraph.getStyle();
        String styleLower = style == null ? "" : style.toLowerCase(Locale.ROOT);
        boolean hasImage = false;
        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                hasImage = true;
                accumulator.addImage(picture.getPictureData());
            }
        }
        if (!text.isEmpty()) {
            BlockType type = blockType(styleLower, text);
            accumulator.addText(type, text);
            accumulator.collect(text);
        } else if (!hasImage) {
            return;
        }
    }

    private static BlockType blockType(String style, String text) {
        if (style.contains("title") || style.equals("documenttitle")) {
            return BlockType.TITLE;
        }
        if (style.contains("heading") || style.matches("h[1-9]")) {
            return BlockType.HEADING;
        }
        if (style.contains("code") || style.contains("source") || text.startsWith("```")
                || text.endsWith("```")) {
            return BlockType.CODE;
        }
        return BlockType.PARAGRAPH;
    }

    private static void parseTable(XWPFTable table, ParseAccumulator accumulator) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(accumulator.sanitize(cell.getText()));
            }
            rows.add(String.join(" | ", cells));
        }
        String text = accumulator.sanitize(String.join("\n", rows));
        if (!text.isEmpty()) {
            accumulator.addTable(text, rows);
            accumulator.collect(text);
        }
    }

    private static final class ParseAccumulator {
        private final ParserRequest request;
        private final ParserLimits limits;
        private final List<ParserWarning> warnings;
        private final List<DocumentBlock> blocks = new ArrayList<>();
        private final List<ParsedAsset> assets = new ArrayList<>();
        private final Map<String, String> assetKeysByPart = new LinkedHashMap<>();
        private final Set<String> invalidParts = new HashSet<>();
        private final Map<String, Placeholder> placeholders = new LinkedHashMap<>();
        private final List<Question> questions = new ArrayList<>();
        private int referencedImageCount;
        private int textChars;
        private long assetBytes;

        private ParseAccumulator(ParserRequest request, ParserLimits limits, List<ParserWarning> warnings) {
            this.request = request;
            this.limits = limits;
            this.warnings = warnings;
        }

        private String sanitize(String text) {
            if (text == null || text.isBlank()) {
                return "";
            }
            StringBuilder clean = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (!Character.isISOControl(character) || character == '\n' || character == '\r' || character == '\t') {
                    clean.append(character);
                }
            }
            String normalized = clean.toString().trim();
            if (normalized.length() > limits.maxTextChars()
                    || textChars > limits.maxTextChars() - normalized.length()) {
                throw new DocumentParseException("document text exceeds the configured limit");
            }
            return normalized;
        }

        private void addText(BlockType type, String text) {
            addBlock(new DocumentBlock(type, text, List.of(), null));
        }

        private void addTable(String text, List<String> rows) {
            addBlock(new DocumentBlock(BlockType.TABLE, text, rows, null));
        }

        private void addImage(XWPFPictureData pictureData) {
            if (blocks.size() >= limits.maxBlocks()) {
                throw new DocumentParseException("document contains too many blocks");
            }
            referencedImageCount++;
            blocks.add(new DocumentBlock(BlockType.IMAGE, null, List.of(), extractAsset(pictureData)));
        }

        /** Returns the secure object key, or null when the binary is rejected. */
        private String extractAsset(XWPFPictureData pictureData) {
            if (pictureData == null) {
                warnings.add(new ParserWarning("IMAGE_DATA_MISSING", "image data is missing"));
                return null;
            }
            String partName = packagePartName(pictureData);
            String existing = assetKeysByPart.get(partName);
            if (existing != null) {
                return existing;
            }
            if (invalidParts.contains(partName)) {
                return null;
            }
            if (assets.size() >= limits.maxAssets()) {
                throw new DocumentParseException("document contains too many binary assets");
            }
            byte[] bytes;
            try {
                bytes = pictureData.getData();
            } catch (RuntimeException failure) {
                warnings.add(new ParserWarning("IMAGE_READ_FAILED", "image data could not be read"));
                invalidParts.add(partName);
                return null;
            }
            if (bytes == null || bytes.length == 0) {
                warnings.add(new ParserWarning("IMAGE_EMPTY", "image data is empty"));
                invalidParts.add(partName);
                return null;
            }
            if (bytes.length > limits.maxAssetBytes()
                    || assetBytes > limits.maxTotalAssetBytes() - bytes.length) {
                throw new DocumentParseException("document assets exceed the configured limit");
            }
            String mediaType;
            try {
                mediaType = normalizeMediaType(pictureData.getPackagePart().getContentType());
            } catch (RuntimeException failure) {
                warnings.add(new ParserWarning("IMAGE_TYPE_REJECTED", "embedded image type is unavailable"));
                invalidParts.add(partName);
                return null;
            }
            if (!SUPPORTED_ASSET_TYPES.contains(mediaType) || !signatureMatches(mediaType, bytes)) {
                warnings.add(new ParserWarning("IMAGE_TYPE_REJECTED", "embedded image type or signature is not allowed"));
                invalidParts.add(partName);
                return null;
            }
            String originalFileName = safeAssetName(pictureData.getFileName(), mediaType, assets.size() + 1);
            String objectKey = DocumentAssetKeyFactory.forDocument(request.tenantId(),
                    request.documentId() == null ? request.sourceObjectKey() : request.documentId(),
                    assets.size() + 1, originalFileName);
            String checksum = sha256(bytes);
            DocumentAsset metadata = new DocumentAsset(objectKey, originalFileName, mediaType,
                    bytes.length, checksum);
            assets.add(new ParsedAsset(metadata, bytes));
            assetKeysByPart.put(partName, objectKey);
            assetBytes += bytes.length;
            return objectKey;
        }

        private void addBlock(DocumentBlock block) {
            if (blocks.size() >= limits.maxBlocks()) {
                throw new DocumentParseException("document contains too many blocks");
            }
            blocks.add(block);
        }

        private void collect(String text) {
            textChars += text.length();
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                String key = matcher.group(1);
                placeholders.putIfAbsent(key, new Placeholder(key, key, true));
            }
            if (QUESTION_MARK.matcher(text).find() || text.contains("思考题") || text.contains("问题")) {
                if (questions.size() < limits.maxBlocks()) {
                    questions.add(new Question("q-" + (questions.size() + 1), text, true));
                }
            }
        }

        private List<DocumentBlock> blocks() {
            return List.copyOf(blocks);
        }

        private List<Placeholder> placeholders() {
            return List.copyOf(placeholders.values());
        }

        private List<Question> questions() {
            return List.copyOf(questions);
        }

        private List<DocumentAsset> assetMetadata() {
            return assets.stream().map(ParsedAsset::metadata).toList();
        }

        private List<ParsedAsset> assets() {
            return List.copyOf(assets);
        }

        private int referencedImageCount() {
            return referencedImageCount;
        }

        private static String packagePartName(XWPFPictureData pictureData) {
            try {
                return pictureData.getPackagePart().getPartName().getName();
            } catch (RuntimeException ignored) {
                return "picture-" + System.identityHashCode(pictureData);
            }
        }

        private static String normalizeMediaType(String mediaType) {
            if (mediaType == null || mediaType.isBlank()) {
                return "";
            }
            return mediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        }

        private static String safeAssetName(String fileName, String mediaType, int ordinal) {
            String fallback = switch (mediaType) {
                case "image/png" -> "image-" + ordinal + ".png";
                case "image/jpeg" -> "image-" + ordinal + ".jpg";
                case "image/gif" -> "image-" + ordinal + ".gif";
                case "image/bmp" -> "image-" + ordinal + ".bmp";
                case "image/tiff" -> "image-" + ordinal + ".tiff";
                default -> "image-" + ordinal;
            };
            if (fileName == null || fileName.isBlank()) {
                return fallback;
            }
            try {
                return DocumentAsset.safeFileName(fileName);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }

        private static boolean signatureMatches(String mediaType, byte[] bytes) {
            return switch (mediaType) {
                case "image/png" -> bytes.length >= 8
                        && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                        && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                        && bytes[6] == 0x1a && bytes[7] == 0x0a;
                case "image/jpeg" -> bytes.length >= 3
                        && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                        && (bytes[2] & 0xff) == 0xff;
                case "image/gif" -> bytes.length >= 6
                        && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                        && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
                case "image/bmp" -> bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M';
                case "image/tiff" -> bytes.length >= 4
                        && ((bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 42 && bytes[3] == 0)
                        || (bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0 && bytes[3] == 42));
                default -> false;
            };
        }

        private static String sha256(byte[] bytes) {
            try {
                return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }
}
