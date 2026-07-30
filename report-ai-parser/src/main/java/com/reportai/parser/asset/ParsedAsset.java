package com.reportai.parser.asset;

import com.reportai.document.model.DocumentAsset;

import java.util.Arrays;
import java.util.Objects;

/** Extracted bytes plus their canonical object-storage metadata. */
public record ParsedAsset(DocumentAsset metadata, byte[] content) {
    public ParsedAsset {
        metadata = Objects.requireNonNull(metadata, "metadata");
        content = Objects.requireNonNull(content, "content").clone();
        if (content.length != metadata.size()) {
            throw new IllegalArgumentException("asset content length does not match metadata");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public String toString() {
        return "ParsedAsset[metadata=" + metadata + ", content=" + Arrays.hashCode(content) + "]";
    }
}
