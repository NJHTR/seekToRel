package com.reportai.parser.security;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Spools and inspects a DOCX archive before POI opens it.
 * No archive entry is ever extracted to a user-controlled path (zip-slip defence).
 */
public final class SecureDocxArchive {
    private static final int BUFFER_SIZE = 16 * 1024;

    private SecureDocxArchive() {
    }

    public static Spool spool(InputStream input, long declaredLength, ParserLimits limits) throws IOException {
        if (declaredLength > limits.maxInputBytes()) {
            throw new UnsafeDocumentException("document exceeds the maximum input size");
        }
        Path temp = Files.createTempFile("report-ai-docx-", ".docx");
        boolean success = false;
        long count = 0;
        byte[] firstBytes = new byte[8];
        int firstCount = 0;
        // The parser boundary does not own the caller's stream; only the temporary file is closed here.
        try (var output = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (count > limits.maxInputBytes() - read) {
                    throw new UnsafeDocumentException("document exceeds the maximum input size");
                }
                output.write(buffer, 0, read);
                if (firstCount < firstBytes.length) {
                    int copy = Math.min(read, firstBytes.length - firstCount);
                    System.arraycopy(buffer, 0, firstBytes, firstCount, copy);
                    firstCount += copy;
                }
                count += read;
            }
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(temp);
            throw failure;
        }
        if (count == 0 || (declaredLength >= 0 && declaredLength != count)) {
            deleteQuietly(temp);
            throw new UnsafeDocumentException("document size is invalid");
        }
        try {
            inspect(temp, limits);
            success = true;
            return new Spool(temp, count, firstBytes);
        } finally {
            if (!success) {
                deleteQuietly(temp);
            }
        }
    }

    public static void inspect(Path archive, ParserLimits limits) throws IOException {
        int entryCount = 0;
        long totalUncompressed = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++entryCount > limits.maxZipEntries()) {
                    throw new UnsafeDocumentException("document contains too many archive entries");
                }
                validateEntryName(entry.getName());
                long declaredSize = entry.getSize();
                if (declaredSize > limits.maxSingleEntryBytes()) {
                    throw new UnsafeDocumentException("document contains an oversized archive entry");
                }
                long compressedSize = entry.getCompressedSize();
                if (declaredSize > 0 && compressedSize > 0
                        && declaredSize / compressedSize > limits.maxCompressionRatio()) {
                    throw new UnsafeDocumentException("document contains a suspicious compression ratio");
                }

                boolean xml = isXml(entry.getName());
                long entryBytes = 0;
                ByteArrayOutputStream xmlBytes = xml
                        ? new ByteArrayOutputStream((int) Math.min(limits.maxXmlEntryBytes(), 64 * 1024L))
                        : null;
                try (InputStream entryInput = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = entryInput.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        if (entryBytes > limits.maxSingleEntryBytes() - read
                                || totalUncompressed > limits.maxUncompressedBytes() - read) {
                            throw new UnsafeDocumentException("document expands beyond the maximum archive size");
                        }
                        entryBytes += read;
                        totalUncompressed += read;
                        if (xml) {
                            if (entryBytes > limits.maxXmlEntryBytes()) {
                                throw new UnsafeDocumentException("document contains an oversized XML entry");
                            }
                            xmlBytes.write(buffer, 0, read);
                        }
                    }
                }
                if (xml) {
                    validateXml(xmlBytes.toByteArray());
                }
            }
        } catch (UnsafeDocumentException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new UnsafeDocumentException("document is not a readable DOCX archive", failure);
        }
    }

    private static void validateEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw new UnsafeDocumentException("document contains an invalid archive entry");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("../") || normalized.contains("/../")
                || normalized.equals("..") || normalized.contains("://")) {
            throw new UnsafeDocumentException("document contains a path traversal entry");
        }
    }

    private static boolean isXml(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xml") || lower.endsWith(".rels");
    }

    private static void validateXml(byte[] xml) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new UnsafeDocumentException("external XML entities are not allowed");
        });
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new UnsafeDocumentException("external XML entities are not allowed");
                    }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            String localName = reader.getAttributeLocalName(i);
                            String value = reader.getAttributeValue(i);
                            if ("TargetMode".equalsIgnoreCase(localName)
                                    && "External".equalsIgnoreCase(value)) {
                                throw new UnsafeDocumentException("external document relationships are not allowed");
                            }
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (UnsafeDocumentException failure) {
            throw failure;
        } catch (XMLStreamException failure) {
            throw new UnsafeDocumentException("document contains invalid XML", failure);
        }
    }

    private static void set(XMLInputFactory factory, String property, Object value) {
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException ignored) {
            // Providers may not expose every optional StAX property; the resolver remains mandatory.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary files are best-effort cleanup; the path is not user-controlled.
        }
    }

    public record Spool(Path path, long bytes, byte[] firstBytes) {
        public Spool {
            firstBytes = firstBytes == null ? new byte[0] : firstBytes.clone();
        }

        public void delete() {
            deleteQuietly(path);
        }
    }
}
