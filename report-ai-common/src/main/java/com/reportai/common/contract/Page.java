package com.reportai.common.contract;

import java.util.List;

/** Immutable keyset page; retentionGap makes partial history explicit to callers. */
public record Page<T>(List<T> items, PageCursor nextCursor, boolean hasMore, boolean retentionGap) {
    public Page {
        items = items == null ? List.of() : List.copyOf(items);
        if (hasMore && nextCursor == null) {
            throw new IllegalArgumentException("nextCursor is required when hasMore is true");
        }
        if (!hasMore && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor must be absent on the final page");
        }
    }

    public static <T> Page<T> first(List<T> items, PageCursor nextCursor, boolean hasMore) {
        return new Page<>(items, nextCursor, hasMore, false);
    }
}
