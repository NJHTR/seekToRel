package com.reportai.llm;

import java.util.Collection;

@FunctionalInterface
public interface TokenEstimator {
    int estimate(String text);

    default int estimate(Collection<String> texts) {
        if (texts == null) {
            return 0;
        }
        long total = texts.stream().mapToLong(value -> estimate(value == null ? "" : value)).sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /** Conservative, provider-neutral approximation used only for safety limits. */
    static int approximate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }

    static int approximate(Collection<String> texts) {
        if (texts == null) {
            return 0;
        }
        long total = texts.stream().mapToLong(value -> approximate(value == null ? "" : value)).sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
