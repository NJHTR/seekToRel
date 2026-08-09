package com.reportai.application.task;

import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.storage.ArtifactStorageResolver;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.StoredObject;
import com.reportai.storage.objectstore.MediaTypeValidator;
import com.reportai.task.ReportTask;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.ReportTaskStatus;
import com.reportai.application.config.properties.ReportAiUploadProperties;
import com.reportai.application.config.properties.ReportAiRuntimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Owns task input objects independently from the report source document.
 * Inputs are content-addressed in the tenant task namespace and are copied to
 * the private workspace only through {@link #materialize(TenantId, String, String)}.
 */
@Service
public final class TaskInputApplicationService {
    private static final String OWNER_TYPE = "TASK_INPUT";
    private static final String ROLE_PREFIX = "INPUT_";
    private static final int MAX_INPUTS = 256;
    /** Materialized by the report boundary; user inputs must not collide with it. */
    private static final String RESERVED_SOURCE_DOCUMENT_PATH = "source-document.docx";
    private static final Pattern PATH_SEGMENT = Pattern.compile("[\\p{L}\\p{N}._@+\\-]+");

    private final AuthorizationPort authorization;
    private final ReportTaskRepository tasks;
    private final ObjectIntegrityRepository integrity;
    private final ObjectProvider<ObjectStorage> objectStorages;
    private final ObjectProvider<ArtifactStorageResolver> artifactResolvers;
    private final IdGenerator ids;
    private final long maxBytes;

    @Autowired
    public TaskInputApplicationService(AuthorizationPort authorization,
                                       ReportTaskRepository tasks,
                                       ObjectIntegrityRepository integrity,
                                       ObjectProvider<ObjectStorage> objectStorages,
                                       ObjectProvider<ArtifactStorageResolver> artifactResolvers,
                                       IdGenerator ids, ReportAiRuntimeProperties runtimeProperties) {
        this(authorization, tasks, integrity, objectStorages, artifactResolvers, ids,
                Objects.requireNonNull(runtimeProperties, "runtimeProperties").getMaxInputBytes());
    }

    /** Compatibility constructor for tests and local callers using the document upload limit. */
    public TaskInputApplicationService(AuthorizationPort authorization,
                                       ReportTaskRepository tasks,
                                       ObjectIntegrityRepository integrity,
                                       ObjectProvider<ObjectStorage> objectStorages,
                                       ObjectProvider<ArtifactStorageResolver> artifactResolvers,
                                       IdGenerator ids, ReportAiUploadProperties uploadProperties) {
        this(authorization, tasks, integrity, objectStorages, artifactResolvers, ids,
                Objects.requireNonNull(uploadProperties, "uploadProperties").getMaxBytes());
    }

    TaskInputApplicationService(AuthorizationPort authorization,
                                ReportTaskRepository tasks,
                                ObjectIntegrityRepository integrity,
                                ObjectProvider<ObjectStorage> objectStorages,
                                ObjectProvider<ArtifactStorageResolver> artifactResolvers,
                                IdGenerator ids, long maxBytes) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.integrity = Objects.requireNonNull(integrity, "integrity");
        this.objectStorages = Objects.requireNonNull(objectStorages, "objectStorages");
        this.artifactResolvers = Objects.requireNonNull(artifactResolvers, "artifactResolvers");
        this.ids = Objects.requireNonNull(ids, "ids");
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        this.maxBytes = maxBytes;
    }

    /** Stores one file under tasks/{tenantHash}/{taskId}/inputs/{relativePath}. */
    public InputMetadata upload(String taskId, String rawPath, MultipartFile file) {
        TenantPrincipal principal = authorized(Permission.TASK_CREATE);
        TenantId tenant = principal.tenantId();
        ReportTask task = findTask(tenant, taskId);
        if (task.status() != ReportTaskStatus.CREATED && task.status() != ReportTaskStatus.PAUSED) {
            throw new BusinessException(ErrorCode.CONFLICT, "task inputs can only be changed before execution");
        }
        String relativePath = safeRelativePath(rawPath);
        if (RESERVED_SOURCE_DOCUMENT_PATH.equals(relativePath)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "input path is reserved for the canonical source document");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "input file must not be empty");
        }
        long size = file.getSize();
        if (size <= 0 || size > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "input file exceeds the permitted size");
        }
        String mediaType = mediaType(file.getContentType());
        String checksum = checksum(file);
        String role = role(relativePath);
        List<ObjectIntegrity> existing = integrity.findByOwner(tenant, OWNER_TYPE, taskId);
        ObjectIntegrity previous = existing.stream().filter(value -> role.equals(value.role())).findFirst().orElse(null);
        StoredObject reference = new StoredObject(objectKey(tenant, taskId, relativePath), mediaType, size, checksum);
        if (previous != null) {
            if (!previous.object().equals(reference)) {
                throw new BusinessException(ErrorCode.CONFLICT, "an input with this path already exists");
            }
            return metadata(relativePath, previous.object());
        }
        ObjectStorage storage = objectStorages.getIfAvailable();
        if (storage == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "object storage is not configured");
        }
        try (InputStream input = file.getInputStream()) {
            storage.put(reference, input);
            integrity.save(new ObjectIntegrity(ids.nextId(), tenant, OWNER_TYPE, taskId, role,
                    reference, Instant.now()));
            return metadata(relativePath, reference);
        } catch (IOException | RuntimeException failure) {
            try { storage.delete(reference.objectKey()); } catch (RuntimeException ignored) { }
            if (failure instanceof BusinessException business) throw business;
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "task input could not be stored");
        }
    }

    public List<InputMetadata> list(String taskId) {
        TenantId tenant = authorized(Permission.TASK_READ).tenantId();
        findTask(tenant, taskId);
        return listForTenant(tenant, taskId);
    }

    /**
     * Materializes all immutable task inputs into workspace/input.  Existing
     * input files are removed first so retries cannot observe stale bytes.
     * The resolver verifies size and SHA-256 while the stream is consumed.
     */
    public Materialization materialize(TenantId tenant, String taskId, String workspaceId, Path workspaceRoot) {
        Objects.requireNonNull(tenant, "tenant");
        requireIdentifier(taskId, "taskId");
        requireIdentifier(workspaceId, "workspaceId");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Path root = workspaceRoot.toAbsolutePath().normalize().resolve(workspaceId).normalize();
        if (!root.startsWith(workspaceRoot.toAbsolutePath().normalize()) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("workspace is invalid");
        }
        try {
            Files.createDirectories(root);
            Path realBase = workspaceRoot.toAbsolutePath().normalize();
            if (!Files.exists(realBase, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(realBase)) {
                throw new IllegalArgumentException("workspace root is invalid");
            }
            Path inputRoot = root.resolve("input").normalize();
            if (!inputRoot.startsWith(root) || Files.isSymbolicLink(inputRoot)) {
                throw new IllegalArgumentException("input workspace is invalid");
            }
            clearDirectory(inputRoot);
            Files.createDirectories(inputRoot);
            List<ObjectIntegrity> records = integrity.findByOwner(tenant, OWNER_TYPE, taskId);
            if (records.size() > MAX_INPUTS) throw new IllegalArgumentException("too many task inputs");
            ArtifactStorageResolver resolver = artifactResolvers.getIfAvailable();
            if (!records.isEmpty() && resolver == null) {
                throw new IllegalStateException("task input storage is not configured");
            }
            List<InputMetadata> materialized = new ArrayList<>();
            for (ObjectIntegrity record : records) {
                String relative = relativePath(taskId, tenant, record.object().objectKey());
                Path target = safeTarget(inputRoot, relative);
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(inputRoot, ".input-", ".tmp");
                try (InputStream input = resolver.open(tenant, record.object())) {
                    Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException | RuntimeException failure) {
                    Files.deleteIfExists(temp);
                    throw failure;
                }
                try {
                    moveAtomically(temp, target);
                } catch (RuntimeException | IOException failure) {
                    Files.deleteIfExists(temp);
                    throw failure;
                }
                materialized.add(metadata(relative, record.object()));
            }
            materialized.sort(Comparator.comparing(InputMetadata::relativePath));
            return new Materialization(List.copyOf(materialized), digest(materialized));
        } catch (IOException failure) {
            throw new IllegalStateException("task inputs could not be materialized", failure);
        }
    }

    private List<InputMetadata> listForTenant(TenantId tenant, String taskId) {
        return integrity.findByOwner(tenant, OWNER_TYPE, taskId).stream()
                .map(value -> metadata(relativePath(taskId, tenant, value.object().objectKey()), value.object()))
                .sorted(Comparator.comparing(InputMetadata::relativePath)).toList();
    }

    /** Removes all staged inputs when an atomic task creation is rolled back. */
    void deleteAll(TenantId tenant, String taskId) {
        requireIdentifier(taskId, "taskId");
        List<ObjectIntegrity> records = integrity.findByOwner(tenant, OWNER_TYPE, taskId);
        ObjectStorage storage = objectStorages.getIfAvailable();
        ArtifactStorageResolver resolver = artifactResolvers.getIfAvailable();
        RuntimeException first = null;
        for (ObjectIntegrity record : records) {
            try {
                relativePath(taskId, tenant, record.object().objectKey());
                if (resolver != null) resolver.delete(tenant, record.object());
                else if (storage != null) storage.delete(record.object().objectKey());
            } catch (RuntimeException failure) {
                if (first == null) first = failure;
            }
        }
        try {
            integrity.deleteByOwner(tenant, OWNER_TYPE, taskId);
        } catch (RuntimeException failure) {
            if (first == null) first = failure;
        }
        if (first != null) throw first;
    }

    private TenantPrincipal authorized(Permission permission) {
        TenantPrincipal principal = authorization.requireAuthenticated();
        if (!principal.tenantId().equals(TenantContext.require())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "tenant context is invalid");
        }
        authorization.requirePermission(permission);
        return principal;
    }

    private ReportTask findTask(TenantId tenant, String taskId) {
        requireIdentifier(taskId, "taskId");
        return tasks.findById(tenant, taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task not found"));
    }

    private static String safeRelativePath(String raw) {
        // ObjectStorage keys are bounded to 512 bytes.  Reserve space for the
        // longest legal tenant and task namespace before accepting a path.
        if (raw == null || raw.isBlank() || raw.length() > 330 || raw.indexOf('\\') >= 0
                || raw.startsWith("/") || raw.endsWith("/") || raw.contains("//")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "input path is invalid");
        }
        String value = raw.strip();
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || !PATH_SEGMENT.matcher(segment).matches()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "input path is invalid");
            }
        }
        return value;
    }

    private static Path safeTarget(Path inputRoot, String relative) {
        Path target = inputRoot.resolve(relative).normalize();
        if (!target.startsWith(inputRoot) || Files.isSymbolicLink(target)) {
            throw new IllegalArgumentException("input path escapes workspace");
        }
        Path current = inputRoot;
        for (String segment : relative.split("/")) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("input path contains symbolic link");
        }
        return target;
    }

    private static void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(directory)) throw new IOException("input directory is symbolic link");
        try (var paths = Files.walk(directory)) {
            List<Path> all = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : all) {
                if (path.equals(directory)) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("symbolic link in input directory");
                Files.deleteIfExists(path);
            }
        }
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String relativePath(String taskId, TenantId tenant, String objectKey) {
        String prefix = "tasks/" + tenantHash(tenant.value()) + "/" + taskId + "/inputs/";
        if (objectKey == null || !objectKey.startsWith(prefix)) {
            throw new IllegalArgumentException("task input object is outside its namespace");
        }
        return safeRelativePath(objectKey.substring(prefix.length()));
    }

    private static String objectKey(TenantId tenant, String taskId, String relative) {
        return "tasks/" + tenantHash(tenant.value()) + "/" + taskId + "/inputs/" + relative;
    }

    private static String tenantHash(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
    }

    private static String role(String relativePath) {
        return ROLE_PREFIX + HexFormat.of().formatHex(sha256(relativePath.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 23).toUpperCase(Locale.ROOT);
    }

    private static InputMetadata metadata(String relative, StoredObject object) {
        return new InputMetadata(relative, object.mediaType(), object.size(), object.sha256());
    }

    private static String mediaType(String raw) {
        if (raw == null || raw.isBlank()) return "application/octet-stream";
        String value = raw.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("text/")) return "application/octet-stream";
        try {
            MediaTypeValidator.validate(value);
            return value;
        } catch (IllegalArgumentException invalid) {
            return "application/octet-stream";
        }
    }

    private static String checksum(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return digest(input);
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "input file could not be read");
        }
    }

    private static String digest(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static String digest(List<InputMetadata> inputs) {
        StringBuilder value = new StringBuilder();
        inputs.stream().sorted(Comparator.comparing(InputMetadata::relativePath)).forEach(input ->
                value.append(input.relativePath()).append('\n').append(input.size()).append('\n')
                        .append(input.sha256()).append('\n'));
        return HexFormat.of().formatHex(sha256(value.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    public record InputMetadata(String relativePath, String mediaType, long size, String sha256) { }

    public record Materialization(List<InputMetadata> inputs, String inputDigest) {
        public Materialization {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            if (inputDigest == null || !inputDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("inputDigest is invalid");
            }
        }
        public Map<String, Object> state() {
            return Map.of("taskInputs", inputs.stream().map(value -> Map.of(
                    "relativePath", value.relativePath(), "mediaType", value.mediaType(),
                    "size", value.size(), "sha256", value.sha256())).toList(),
                    "taskInputDigest", inputDigest);
        }
    }
}
