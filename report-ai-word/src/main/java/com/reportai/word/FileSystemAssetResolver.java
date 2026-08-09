package com.reportai.word;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional local development resolver. It confines every asset to a real root directory,
 * including symlink targets; production deployments should normally use an object-storage adapter.
 */
public final class FileSystemAssetResolver implements AssetResolver {
    private final Path root;

    public FileSystemAssetResolver(Path root) {
        try {
            this.root = root.toRealPath();
        } catch (IOException failure) {
            throw new IllegalArgumentException("asset root is unavailable", failure);
        }
    }

    @Override
    public Optional<Asset> resolve(String assetKey) throws IOException {
        validate(assetKey);
        Path candidate = root.resolve(assetKey).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        Path real = candidate.toRealPath();
        if (!real.startsWith(root)) {
            throw new WordExportException("unsafe asset reference");
        }
        String mediaType = Files.probeContentType(real);
        if (mediaType == null) {
            mediaType = "application/octet-stream";
        }
        return Optional.of(new Asset(mediaType.toLowerCase(Locale.ROOT), Files.newInputStream(real), Files.size(real)));
    }

    private static void validate(String key) {
        if (key == null || key.isBlank() || key.indexOf('\0') >= 0 || key.indexOf('\\') >= 0
                || key.startsWith("/") || key.matches("[A-Za-z]:.*")) {
            throw new WordExportException("unsafe asset reference");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new WordExportException("unsafe asset reference");
            }
        }
    }
}
