package com.reportai.word;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Resolves a logical object key; implementations must not expose local paths to callers. */
@FunctionalInterface
public interface AssetResolver {
    Optional<Asset> resolve(String assetKey) throws IOException;

    static AssetResolver none() {
        return ignored -> Optional.empty();
    }

    record Asset(String mediaType, InputStream content, long declaredSize) {
        public Asset {
            if (mediaType == null || mediaType.isBlank() || content == null) {
                throw new IllegalArgumentException("asset mediaType and content are required");
            }
            if (declaredSize < -1) {
                throw new IllegalArgumentException("declaredSize must be -1 or non-negative");
            }
        }
    }
}
