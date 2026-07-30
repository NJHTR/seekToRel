package com.reportai.llm;

import java.net.URI;
import java.util.Arrays;

public record VisionInput(String mediaType, byte[] data, URI uri, String text) {
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    public VisionInput {
        if (mediaType == null || mediaType.isBlank() || mediaType.length() > 128) {
            throw new IllegalArgumentException("mediaType is invalid");
        }
        int sources = (data == null ? 0 : 1) + (uri == null ? 0 : 1) + (text == null || text.isBlank() ? 0 : 1);
        if (sources != 1) {
            throw new IllegalArgumentException("exactly one vision input source is required");
        }
        if (data != null) {
            if (data.length == 0 || data.length > MAX_BYTES) {
                throw new IllegalArgumentException("vision input exceeds the maximum allowed size");
            }
            data = Arrays.copyOf(data, data.length);
        }
        if (uri != null && !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("vision URI must use http or https");
        }
        if (text != null && text.length() > 1_000_000) {
            throw new IllegalArgumentException("vision text is too long");
        }
    }

    @Override
    public byte[] data() {
        return data == null ? null : Arrays.copyOf(data, data.length);
    }
}
