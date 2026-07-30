package com.reportai.word;

import java.util.Set;

/** Immutable safety and layout limits for DOCX export. */
public record WordBuildOptions(
        long maxAssetBytes,
        int maxImageWidth,
        int maxImageHeight,
        Set<String> allowedImageMediaTypes,
        AssetResolver assetResolver) {

    public WordBuildOptions {
        if (maxAssetBytes <= 0 || maxAssetBytes > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("maxAssetBytes must be between 1 and 104857600");
        }
        if (maxImageWidth <= 0 || maxImageHeight <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        allowedImageMediaTypes = allowedImageMediaTypes == null || allowedImageMediaTypes.isEmpty()
                ? Set.of("image/png", "image/jpeg", "image/gif")
                : allowedImageMediaTypes.stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toUnmodifiableSet());
        assetResolver = assetResolver == null ? AssetResolver.none() : assetResolver;
    }

    public static WordBuildOptions defaults() {
        return new WordBuildOptions(10 * 1024 * 1024, 4_096, 4_096,
                Set.of("image/png", "image/jpeg", "image/gif"), AssetResolver.none());
    }
}
