package com.reportai.common.contract;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Small dependency-free canonical JSON encoder for digesting contract values. */
public final class CanonicalJson {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_ENTRIES = 10_000;

    private CanonicalJson() {
    }

    public static String encode(Object value) {
        StringBuilder out = new StringBuilder(256);
        append(out, value, 0, new int[] {0});
        return out.toString();
    }

    private static void append(StringBuilder out, Object value, int depth, int[] entries) {
        if (depth > MAX_DEPTH || ++entries[0] > MAX_ENTRIES) {
            throw new IllegalArgumentException("canonical value exceeds bounds");
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(out, text);
        } else if (value instanceof Boolean bool) {
            out.append(bool);
        } else if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)
                    || number instanceof Float f && !Float.isFinite(f)) {
                throw new IllegalArgumentException("non-finite number is not valid JSON");
            }
            out.append(new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            map.keySet().stream().map(key -> {
                if (!(key instanceof String)) {
                    throw new IllegalArgumentException("canonical object keys must be strings");
                }
                return (String) key;
            }).sorted(Comparator.naturalOrder()).forEachOrdered(key -> {
                if (out.charAt(out.length() - 1) != '{') out.append(',');
                quote(out, key);
                out.append(':');
                append(out, map.get(key), depth + 1, entries);
            });
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean[] first = {true};
            iterable.forEach(item -> {
                if (!first[0]) out.append(',');
                first[0] = false;
                append(out, item, depth + 1, entries);
            });
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            for (int index = 0; index < Array.getLength(value); index++) {
                if (index > 0) out.append(',');
                append(out, Array.get(value, index), depth + 1, entries);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("unsupported canonical value type: " + value.getClass().getName());
        }
    }

    private static void quote(StringBuilder out, String value) {
        Objects.requireNonNull(value, "value");
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) out.append(String.format("\\u%04x", (int) character));
                    else out.append(character);
                }
            }
        }
        out.append('"');
    }
}
