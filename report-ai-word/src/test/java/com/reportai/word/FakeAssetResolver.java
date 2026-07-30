package com.reportai.word;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/** Deterministic in-memory resolver used by unit tests and local adapter checks. */
final class FakeAssetResolver implements AssetResolver {
    private final Map<String, Asset> assets;

    FakeAssetResolver(Map<String, Asset> assets) {
        this.assets = Map.copyOf(assets);
    }

    static FakeAssetResolver of(String key, String mediaType, byte[] bytes) {
        return new FakeAssetResolver(Map.of(key, new Asset(mediaType, new ByteArrayInputStream(bytes), bytes.length)));
    }

    @Override
    public Optional<Asset> resolve(String assetKey) throws IOException {
        Asset asset = assets.get(assetKey);
        if (asset == null) return Optional.empty();
        byte[] copy = asset.content().readAllBytes();
        return Optional.of(new Asset(asset.mediaType(), new ByteArrayInputStream(copy), copy.length));
    }
}
