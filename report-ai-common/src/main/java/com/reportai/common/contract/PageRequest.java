package com.reportai.common.contract;

public record PageRequest(int limit, PageCursor after) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public PageRequest {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static PageRequest defaults() {
        return new PageRequest(DEFAULT_LIMIT, null);
    }
}
