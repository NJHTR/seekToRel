package com.reportai.llm;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * A content part accepted by OpenAI-compatible multimodal chat endpoints.
 * Text-only callers continue to use {@link ChatMessage#ChatMessage(Role, String)};
 * image parts are intentionally represented as bounded data URLs so the LLM
 * provider never needs access to object storage credentials or host paths.
 */
public record ChatContentPart(String type, String text, String mediaType, String data) {
    private static final int MAX_TEXT_CHARS = 1_000_000;
    private static final int MAX_DATA_CHARS = 32 * 1024 * 1024;
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "^[A-Za-z0-9!#$&^_.+\\-]+/[A-Za-z0-9!#$&^_.+\\-]+$");

    public ChatContentPart {
        type = Objects.requireNonNull(type, "type").trim().toLowerCase(Locale.ROOT);
        if (!"text".equals(type) && !"image_url".equals(type)) {
            throw new IllegalArgumentException("unsupported chat content part type");
        }
        if ("text".equals(type)) {
            if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS) {
                throw new IllegalArgumentException("text content part is invalid");
            }
            text = text;
            mediaType = null;
            data = null;
        } else {
            if (mediaType == null || !MEDIA_TYPE.matcher(mediaType.strip()).matches()) {
                throw new IllegalArgumentException("image media type is invalid");
            }
            mediaType = mediaType.strip().toLowerCase(Locale.ROOT);
            if (data == null || data.isBlank() || data.length() > MAX_DATA_CHARS
                    || (!isHttpUrl(data) && !isBase64(data))) {
                throw new IllegalArgumentException("image data is invalid");
            }
            text = null;
        }
    }

    public static ChatContentPart text(String value) {
        return new ChatContentPart("text", value, null, null);
    }

    public static ChatContentPart image(String mediaType, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("image bytes must not be empty");
        }
        return new ChatContentPart("image_url", null, mediaType,
                Base64.getEncoder().encodeToString(bytes));
    }

    /** Creates an image part backed by a provider-fetchable HTTPS/HTTP URL. */
    public static ChatContentPart imageUrl(String mediaType, URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("image URL must use http or https");
        }
        return new ChatContentPart("image_url", null, mediaType, uri.toString());
    }

    /** Converts this part to the content shape defined by the OpenAI API. */
    public Map<String, Object> toOpenAiPayload() {
        if ("text".equals(type)) {
            return Map.of("type", "text", "text", text);
        }
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", isHttpUrl(data) ? data : "data:" + mediaType + ";base64," + data);
        return Map.of("type", "image_url", "image_url", image);
    }

    private static boolean isBase64(String value) {
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "https://", 0, 8)
                || value.regionMatches(true, 0, "http://", 0, 7);
    }
}
