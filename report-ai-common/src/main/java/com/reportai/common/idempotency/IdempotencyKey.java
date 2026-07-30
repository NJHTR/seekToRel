package com.reportai.common.idempotency;

import java.util.Objects;
import java.util.regex.Pattern;

public record IdempotencyKey(String value) {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    public IdempotencyKey {
        value = Objects.requireNonNull(value, "idempotencyKey").trim();
        if (!SAFE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("idempotencyKey must be 8-128 safe characters");
        }
    }
}
