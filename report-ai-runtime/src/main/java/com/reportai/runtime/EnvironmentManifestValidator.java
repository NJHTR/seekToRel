package com.reportai.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Performs the no-side-effect security and input checks before a sandbox starts. */
public final class EnvironmentManifestValidator {
    private static final long MAX_SCAN_BYTES = 10L * 1024 * 1024 * 1024;
    private static final int MAX_SCAN_FILES = 100_000;

    public PreflightResult validate(EnvironmentManifest manifest, Path workspaceRoot) {
        List<PreflightResult.Violation> violations = new ArrayList<>();
        if (manifest == null) {
            return new PreflightResult(false, List.of(new PreflightResult.Violation(
                    "MANIFEST_MISSING", "", "environment manifest is required")));
        }
        if (workspaceRoot == null) {
            return new PreflightResult(false, List.of(new PreflightResult.Violation(
                    "WORKSPACE_MISSING", "", "workspace root is required")));
        }
        Path root = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            violations.add(new PreflightResult.Violation("WORKSPACE_MISSING", "", "workspace does not exist"));
        }
        Path inputRoot = root.resolve("input").normalize();
        String extension = languageExtension(manifest.language());
        if (extension == null) {
            violations.add(new PreflightResult.Violation("LANGUAGE_UNSUPPORTED", manifest.language(),
                    "language is not enabled by the runtime policy"));
        } else if (!manifest.entryPoint().toLowerCase(java.util.Locale.ROOT).endsWith(extension)) {
            violations.add(new PreflightResult.Violation("ENTRYPOINT_INVALID", manifest.entryPoint(),
                    "entry point extension does not match language"));
        }
        for (DependencySpec dependency : manifest.dependencies()) {
            if (dependency.sha256() == null) {
                violations.add(new PreflightResult.Violation("DEPENDENCY_HASH_MISSING", dependency.name(),
                        "dependency must include a sha256 hash in a locked manifest"));
            }
        }
        if (requiresScientificInputs(manifest) && manifest.inputs().isEmpty()) {
            violations.add(new PreflightResult.Violation("PYTHON_INPUT_MISSING", "",
                    "Torch/TorchVision experiments must declare real dataset inputs; synthetic fixtures are not accepted"));
        }
        for (InputRequirement input : manifest.inputs()) {
            validateInput(inputRoot, input, violations);
        }
        return violations.isEmpty() ? PreflightResult.success() : new PreflightResult(false, violations);
    }

    private static void validateInput(Path root, InputRequirement input, List<PreflightResult.Violation> violations) {
        Path path = root.resolve(input.relativePath()).normalize();
        if (!path.startsWith(root) || hasSymlinkAncestor(root, path)) {
            violations.add(new PreflightResult.Violation("INPUT_PATH_INVALID", input.relativePath(),
                    "input path escapes workspace or is a symbolic link"));
            return;
        }
        boolean exists = input.kind() == InputRequirement.Kind.FILE ? Files.isRegularFile(path) : Files.isDirectory(path);
        if (!exists) {
            violations.add(new PreflightResult.Violation("INPUT_MISSING", input.relativePath(), "required input is missing"));
            return;
        }
        try {
            long size = input.kind() == InputRequirement.Kind.FILE ? Files.size(path) : directorySize(path);
            if (size > input.maxBytes()) {
                violations.add(new PreflightResult.Violation("INPUT_TOO_LARGE", input.relativePath(),
                        "required input exceeds maxBytes"));
                return;
            }
            if (input.sha256() != null && input.kind() == InputRequirement.Kind.FILE
                    && !input.sha256().equalsIgnoreCase(sha256(path))) {
                violations.add(new PreflightResult.Violation("INPUT_HASH_MISMATCH", input.relativePath(),
                        "required input sha256 does not match"));
            }
        } catch (IOException ex) {
            violations.add(new PreflightResult.Violation("INPUT_UNREADABLE", input.relativePath(),
                    "required input cannot be inspected"));
        }
    }

    private static boolean hasSymlinkAncestor(Path root, Path path) {
        Path current = path;
        while (current != null && current.startsWith(root)) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
            if (current.equals(root)) {
                return false;
            }
            current = current.getParent();
        }
        return true;
    }

    private static long directorySize(Path path) throws IOException {
        try (var stream = Files.walk(path, 16)) {
            long[] total = {0};
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            if (files.size() > MAX_SCAN_FILES) {
                throw new IOException("too many files");
            }
            for (Path child : files) {
                if (Files.isSymbolicLink(child)) throw new IOException("symbolic link");
                total[0] = Math.addExact(total[0], Files.size(child));
                if (total[0] > MAX_SCAN_BYTES) return total[0];
            }
            return total[0];
        }
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static String languageExtension(String language) {
        return switch (language.toLowerCase(java.util.Locale.ROOT)) {
            case "python" -> ".py";
            case "java" -> ".java";
            case "cpp", "c++" -> ".cpp";
            default -> null;
        };
    }

    private static boolean requiresScientificInputs(EnvironmentManifest manifest) {
        if (!"python".equalsIgnoreCase(manifest.language())
                || !"scientific".equalsIgnoreCase(manifest.capabilityProfile())) {
            return false;
        }
        return manifest.dependencies().stream().anyMatch(dependency ->
                dependency.name().equalsIgnoreCase("torch")
                        || dependency.name().equalsIgnoreCase("torchvision"));
    }
}
