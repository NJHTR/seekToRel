package com.reportai.llm;

import java.net.URI;
import java.util.Arrays;

public record OcrInput(String mediaType, byte[] data, URI uri) {
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    public OcrInput {
        if (mediaType == null || mediaType.isBlank() || mediaType.length() > 128) {
            throw new IllegalArgumentException("mediaType is invalid");
        }
        if ((data == null) == (uri == null)) {
            throw new IllegalArgumentException("exactly one OCR input source is required");
        }
        if (data != null) {
            if (data.length == 0 || data.length > MAX_BYTES) {
                throw new IllegalArgumentException("OCR input exceeds the maximum allowed size");
            }
            data = Arrays.copyOf(data, data.length);
        }
        if (uri != null && !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("OCR URI must use http or https");
        }
    }

    @Override
    public byte[] data() {
        return data == null ? null : Arrays.copyOf(data, data.length);
    }
}
