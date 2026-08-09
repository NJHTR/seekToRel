package com.reportai.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

/**
 * Filesystem-backed workspace preparation with bounded, relative paths and
 * symlink checks. The runtime root is application configuration, never input
 * from an agent or HTTP request.
 */
public final class LocalWorkspaceFileStore implements WorkspaceFileStore {
    private static final Pattern WORKSPACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern RELATIVE_PATH = Pattern.compile("[A-Za-z0-9_./-]{1,256}");
    private static final long MAX_SOURCE_BYTES = 512L * 1024;
    private final Path root;

    public LocalWorkspaceFileStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("workspace root is required");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
            rejectSymlink(this.root, "workspace root");
        } catch (IOException failure) {
            throw new IllegalStateException("workspace root cannot be prepared", failure);
        }
    }

    @Override
    public void ensureWorkspace(String workspaceId) {
        Path workspace = workspace(workspaceId);
        try {
            Files.createDirectories(workspace);
            rejectSymlink(workspace, "workspace");
            ensureNoSymlinkAncestors(workspace);
        } catch (IOException failure) {
            throw new IllegalArgumentException("workspace cannot be prepared", failure);
        }
    }

    @Override
    public void writeUtf8(String workspaceId, String relativePath, String content) {
        writeUtf8At(workspaceId, relativePath, content, false);
    }

    @Override
    public void writeUtf8Output(String workspaceId, String relativePath, String content) {
        writeUtf8At(workspaceId, relativePath, content, true);
    }

    private void writeUtf8At(String workspaceId, String relativePath, String content, boolean output) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("source exceeds the permitted size");
        }
        Path workspace = workspace(workspaceId);
        if (output) {
            workspace = workspace.resolve("output").normalize();
            try {
                Files.createDirectories(workspace);
                rejectSymlink(workspace, "output workspace");
            } catch (IOException failure) {
                throw new IllegalArgumentException("output workspace cannot be prepared", failure);
            }
        }
        Path target = safeRelativePath(workspace, relativePath);
        try {
            ensureWorkspace(workspaceId);
            Path parent = target.getParent();
            if (parent == null || !parent.startsWith(workspace)) {
                throw new IllegalArgumentException("source path is invalid");
            }
            Files.createDirectories(parent);
            ensureNoSymlinkAncestors(parent);
            if (Files.isSymbolicLink(target)) {
                throw new IllegalArgumentException("source path is not allowed");
            }
            Path temporary = Files.createTempFile(parent, ".reportai-", ".tmp");
            try {
                Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("source cannot be written to workspace", failure);
        }
    }

    private Path workspace(String workspaceId) {
        if (workspaceId == null || !WORKSPACE_ID.matcher(workspaceId).matches()) {
            throw new IllegalArgumentException("workspaceId is invalid");
        }
        Path workspace = root.resolve(workspaceId).normalize();
        if (!workspace.startsWith(root)) {
            throw new IllegalArgumentException("workspaceId is invalid");
        }
        return workspace;
    }

    private static Path safeRelativePath(Path workspace, String value) {
        if (value == null || value.isBlank() || value.length() > 256 || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0 || !RELATIVE_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("source path is invalid");
        }
        Path relative = Path.of(value).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) {
            throw new IllegalArgumentException("source path is invalid");
        }
        Path target = workspace.resolve(relative).normalize();
        if (!target.startsWith(workspace)) {
            throw new IllegalArgumentException("source path is invalid");
        }
        return target;
    }

    private static void rejectSymlink(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException(label + " is a symbolic link");
        }
    }

    private static void ensureNoSymlinkAncestors(Path path) throws IOException {
        Path current = path;
        while (current != null) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("symbolic link in workspace path");
            }
            current = current.getParent();
        }
    }
}
