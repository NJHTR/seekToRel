package com.reportai.common.contract;

import java.util.Objects;

/** Opaque, bounded keyset cursor. Scope binding/signing is owned by the API adapter. */
public record PageCursor(int schemaVersion, String value, String scope) {
    public PageCursor {
        if (schemaVersion <= 0) throw new IllegalArgumentException("cursor schemaVersion must be positive");
        value = Objects.requireNonNull(value, "cursor value");
        if (value.isBlank() || value.length() > 512 || !value.matches("[A-Za-z0-9._~:-]+")) {
            throw new IllegalArgumentException("cursor value is invalid");
        }
        scope = Objects.requireNonNull(scope, "cursor scope");
        if (scope.isBlank() || scope.length() > 256 || scope.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("cursor scope is invalid");
        }
    }
}
