package com.reportai.application.task;

import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.StoredObject;
import com.reportai.common.id.IdGenerator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Persists the bounded output of a sandbox execution before review starts.
 *
 * <p>The runtime returns logical workspace-relative names only.  This service is
 * the only boundary which turns those names into durable objects: it confines
 * every read to the configured workspace, verifies the bytes while uploading,
 * and records an immutable checksum reference.  Reviewer and document agents
 * must consume the returned references instead of host paths.</p>
 */
public final class ExecutionArtifactPersistenceService {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    /**
     * A logical artifact path is a workspace-relative display name, not an
     * object-store key.  Keep the syntax deliberately path-oriented instead
     * of imposing an ASCII filename policy: generated reports commonly use
     * Chinese names and spaces.
     */
    private static final int MAX_ARTIFACT_PATH_LENGTH = 512;
    private static final int MAX_ARTIFACT_SEGMENT_LENGTH = 255;
    private static final int MAX_ARTIFACTS = 256;
    private static final long MAX_LOG_BYTES = 2L * 1024 * 1024;
    private static final String BINARY_MEDIA_TYPE = "application/octet-stream";

    private final Path workspaceRoot;
    private final ObjectStorage storage;
    private final ObjectIntegrityRepository integrity;
    private final IdGenerator ids;
    private final long maxArtifactBytes;

    public ExecutionArtifactPersistenceService(Path workspaceRoot, ObjectStorage storage,
                                               ObjectIntegrityRepository integrity, IdGenerator ids,
                                               long maxArtifactBytes) {
        if (workspaceRoot == null || storage == null || integrity == null || ids == null) {
            throw new NullPointerException("workspaceRoot, storage, integrity and ids are required");
        }
        if (maxArtifactBytes <= 0) {
            throw new IllegalArgumentException("maxArtifactBytes must be positive");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.storage = storage;
        this.integrity = integrity;
        this.ids = ids;
        this.maxArtifactBytes = maxArtifactBytes;
    }

    /**
     * Persists artifact files and stdout/stderr from a tool result.  The returned
     * maps are immutable and safe to place in the JSON checkpoint state.
     */
    public PersistedArtifacts persist(TenantId tenantId, String taskId, String workspaceId,
                                      Map<?, ?> execution) {
        Objects.requireNonNull(tenantId, "tenantId");
        requireIdentifier(taskId, "taskId");
        requireIdentifier(workspaceId, "workspaceId");
        if (execution == null) {
            throw new IllegalArgumentException("execution output is required");
        }
        Path workspace = workspace(workspaceId);
        List<String> keys = artifactKeys(execution.get("artifactKeys"));
        List<Map<String, Object>> references = new ArrayList<>(keys.size());
        for (String key : keys) {
            references.add(persistFile(tenantId, taskId, workspace, key, "ARTIFACT"));
        }
        Map<String, Object> stdout = persistText(tenantId, taskId, execution.get("stdout"), "EXECUTION_STDOUT");
        Map<String, Object> stderr = persistText(tenantId, taskId, execution.get("stderr"), "EXECUTION_STDERR");
        return new PersistedArtifacts(references, stdout, stderr);
    }

    private Map<String, Object> persistText(TenantId tenantId, String taskId, Object value, String role) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(role + " must be text");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            return null;
        }
        if (bytes.length > MAX_LOG_BYTES) {
            throw new IllegalArgumentException(role + " exceeds the permitted size");
        }
        String digest = sha256(bytes);
        String objectKey = objectKey(tenantId, taskId, "logs/" + role.toLowerCase(Locale.ROOT) + "-" + digest + ".log");
        StoredObject reference = new StoredObject(objectKey, BINARY_MEDIA_TYPE, bytes.length, digest);
        persistObject(tenantId, taskId, role, reference, new ByteArrayInputStream(bytes));
        return referenceMap("", reference);
    }

    private Map<String, Object> persistFile(TenantId tenantId, String taskId, Path workspace,
                                            String key, String rolePrefix) {
        Path candidate = safeArtifact(workspace, key);
        long size;
        try {
            size = Files.size(candidate);
        } catch (IOException failure) {
            throw new IllegalArgumentException("artifact cannot be inspected: " + key, failure);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("artifact is empty: " + key);
        }
        if (size > maxArtifactBytes) {
            throw new IllegalArgumentException("artifact exceeds the permitted size: " + key);
        }
        String mediaType = mediaType(candidate);
        String digest;
        try (InputStream input = Files.newInputStream(candidate)) {
            digest = digest(input, size);
        } catch (IOException failure) {
            throw new IllegalArgumentException("artifact cannot be read: " + key, failure);
        }
        // Keep the user-facing logical path in the reference, but never place
        // it directly in an object-store key.  Besides avoiding provider
        // encoding differences, this makes retries and same-content/different-
        // filename outputs deterministic and collision-free.
        String logicalPathDigest = sha256(key.getBytes(StandardCharsets.UTF_8));
        String objectKey = objectKey(tenantId, taskId,
                "artifacts/" + digest + "/" + logicalPathDigest);
        StoredObject reference = new StoredObject(objectKey, mediaType, size, digest);
        // The role is keyed by the logical path, not only by content. Two
        // outputs may legitimately have identical bytes; using the content hash
        // here would make PostgreSQL's owner/role uniqueness overwrite one
        // artifact's metadata with the other.
        String role = rolePrefix + "_" + sha256(key.getBytes(StandardCharsets.UTF_8))
                .substring(0, 23).toUpperCase(Locale.ROOT);
        try (InputStream input = Files.newInputStream(candidate)) {
            persistObject(tenantId, taskId, role, reference, input);
        } catch (IOException failure) {
            throw new IllegalArgumentException("artifact cannot be uploaded: " + key, failure);
        }
        return referenceMap(key, reference);
    }

    private void persistObject(TenantId tenantId, String taskId, String role,
                               StoredObject reference, InputStream data) {
        // Retries must converge on one immutable integrity record. The artifact
        // key is deterministic from content and logical path, so an existing
        // matching record is already the authoritative result of a prior attempt.
        for (ObjectIntegrity existing : integrity.findByOwner(tenantId, "TASK", taskId)) {
            if (role.equals(existing.role()) && reference.equals(existing.object())) {
                return;
            }
        }
        try {
            storage.put(reference, data);
            integrity.save(new ObjectIntegrity(ids.nextId(), tenantId, "TASK", taskId,
                    role, reference, Instant.now()));
        } catch (RuntimeException failure) {
            try {
                storage.delete(reference.objectKey());
            } catch (RuntimeException ignored) {
                // Preserve the original storage or metadata failure. Reconciliation
                // can remove an orphaned object once the provider is reachable.
            }
            throw failure;
        }
    }

    private Path workspace(String workspaceId) {
        Path workspace = workspaceRoot.resolve(workspaceId).normalize();
        if (!workspace.startsWith(workspaceRoot) || !Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(workspace)) {
            throw new IllegalArgumentException("workspace is unavailable");
        }
        try {
            Path realRoot = workspaceRoot.toRealPath();
            Path realWorkspace = workspace.toRealPath();
            if (!realWorkspace.startsWith(realRoot)) {
                throw new IllegalArgumentException("workspace escapes runtime root");
            }
            try (var paths = Files.walk(realWorkspace, 8)) {
                if (paths.anyMatch(Files::isSymbolicLink)) {
                    throw new IllegalArgumentException("symbolic links in workspace are not allowed");
                }
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("workspace cannot be inspected", failure);
        }
        return workspace;
    }

    private static Path safeArtifact(Path workspace, String key) {
        Path relative = safeArtifactPathOnly(key);
        Path candidate = workspace.resolve(relative).normalize();
        if (!candidate.startsWith(workspace) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(candidate)) {
            throw new IllegalArgumentException("artifact is unavailable: " + key);
        }
        Path ancestor = workspace;
        for (Path part : relative) {
            ancestor = ancestor.resolve(part);
            if (Files.isSymbolicLink(ancestor)) {
                throw new IllegalArgumentException("symbolic links in artifact path are not allowed");
            }
        }
        try {
            if (!candidate.toRealPath().startsWith(workspace.toRealPath())) {
                throw new IllegalArgumentException("artifact escapes workspace");
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("artifact cannot be inspected: " + key, failure);
        }
        return candidate;
    }

    private static List<String> artifactKeys(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values) || values.size() > MAX_ARTIFACTS) {
            throw new IllegalArgumentException("artifactKeys must be a bounded array");
        }
        List<String> keys = new ArrayList<>(values.size());
        for (Object candidate : values) {
            if (!(candidate instanceof String key)) {
                throw new IllegalArgumentException("artifact key must be text");
            }
            // Validate before touching the filesystem and reject duplicates so the
            // immutable checkpoint has one canonical reference per logical output.
            safeArtifactPathOnly(key);
            if (!keys.contains(key)) keys.add(key);
        }
        return List.copyOf(keys);
    }

    private static Path safeArtifactPathOnly(String key) {
        return safeArtifactPathOnlyPath(key);
    }

    private static Path safeArtifactPathOnlyPath(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_ARTIFACT_PATH_LENGTH
                || !key.equals(key.strip()) || key.startsWith("/") || key.endsWith("/")
                || key.contains("//") || key.indexOf('\\') >= 0 || key.indexOf(':') >= 0
                || key.indexOf('\u0000') >= 0 || key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("artifact key is invalid");
        }
        String[] segments = key.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.length() > MAX_ARTIFACT_SEGMENT_LENGTH
                    || segment.endsWith(".") || segment.endsWith(" ")) {
                throw new IllegalArgumentException("artifact key is invalid");
            }
        }
        final Path relative;
        try {
            relative = Path.of(key);
        } catch (InvalidPathException failure) {
            throw new IllegalArgumentException("artifact key is invalid", failure);
        }
        if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) {
            throw new IllegalArgumentException("artifact key escapes workspace");
        }
        return relative;
    }

    private static String mediaType(Path path) {
        try {
            String value = Files.probeContentType(path);
            if (value == null || value.isBlank() || value.toLowerCase(Locale.ROOT).startsWith("text/")) {
                return BINARY_MEDIA_TYPE;
            }
            return value.toLowerCase(Locale.ROOT);
        } catch (IOException ignored) {
            return BINARY_MEDIA_TYPE;
        }
    }

    private static String objectKey(TenantId tenantId, String taskId, String suffix) {
        String tenantDigest = sha256(tenantId.value().getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        String key = "tasks/" + tenantDigest + "/" + taskId + "/" + suffix;
        if (key.length() > 512) throw new IllegalArgumentException("artifact object key is too long");
        return key;
    }

    private static Map<String, Object> referenceMap(String logicalKey, StoredObject reference) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (logicalKey != null && !logicalKey.isBlank()) value.put("logicalKey", logicalKey);
        value.put("objectKey", reference.objectKey());
        value.put("mediaType", reference.mediaType());
        value.put("size", reference.size());
        value.put("sha256", reference.sha256());
        return Map.copyOf(value);
    }

    private static String digest(InputStream input, long expectedSize) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            count += read;
            if (count > expectedSize) throw new IOException("artifact changed while reading");
            digest.update(buffer, 0, read);
        }
        if (count != expectedSize) throw new IOException("artifact size changed while reading");
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    public record PersistedArtifacts(List<Map<String, Object>> artifactReferences,
                                     Map<String, Object> stdoutReference,
                                     Map<String, Object> stderrReference) {
        public PersistedArtifacts {
            artifactReferences = artifactReferences == null ? List.of()
                    : artifactReferences.stream().map(Map::copyOf).toList();
            artifactReferences = List.copyOf(artifactReferences);
            stdoutReference = stdoutReference == null ? null : Map.copyOf(stdoutReference);
            stderrReference = stderrReference == null ? null : Map.copyOf(stderrReference);
        }

        public Map<String, Object> state() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("artifactReferences", artifactReferences);
            if (stdoutReference != null) state.put("stdoutReference", stdoutReference);
            if (stderrReference != null) state.put("stderrReference", stderrReference);
            return Map.copyOf(state);
        }
    }
}
