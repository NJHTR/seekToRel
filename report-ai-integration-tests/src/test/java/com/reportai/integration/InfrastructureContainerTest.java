package com.reportai.integration;

import com.reportai.common.tenant.TenantId;
import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.idempotency.IdempotencyRecord;
import com.reportai.document.model.DocumentAssetKeyFactory;
import com.reportai.runtime.DockerJavaSandboxClient;
import com.reportai.runtime.DockerRuntimeEngine;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.EnvironmentCatalog;
import com.reportai.runtime.ExecutionPolicy;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.LanguageRuntime;
import com.reportai.runtime.RuntimeImageProfile;
import com.reportai.runtime.RuntimeConfiguration;
import com.reportai.runtime.RuntimeInstallResult;
import com.reportai.runtime.RuntimeLifecycleEngine;
import com.reportai.runtime.PythonDependencyRequest;
import com.reportai.scheduler.QueuedTask;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventCursor;
import com.reportai.scheduler.ProgressEventType;
import com.reportai.scheduler.RetryPolicy;
import com.reportai.scheduler.SchedulerOutboxEntry;
import com.reportai.storage.StoredObject;
import com.reportai.storage.objectstore.MinioObjectStorage;
import com.reportai.storage.objectstore.ObjectStorageProperties;
import com.reportai.storage.objectstore.TenantObjectStorageResolver;
import com.reportai.storage.objectstore.TenantArtifactStorageResolver;
import com.reportai.storage.postgres.PostgresIdempotencyStore;
import com.reportai.storage.postgres.PostgresSchedulerTaskStore;
import com.reportai.storage.postgres.PostgresSchedulerOutboxRepository;
import com.reportai.storage.postgres.PostgresProgressEventHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real infrastructure evidence. This class is only built by the explicit
 * {@code -Pintegration} Maven profile, so ordinary development remains Docker-free.
 */
@Testcontainers
class InfrastructureContainerTest {
    private static final String MINIO_ACCESS_KEY = "reportai_test";
    private static final String MINIO_SECRET_KEY = "reportai_test_secret";
    private static final String MINIO_BUCKET = "report-ai-test";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
            .withDatabaseName("report_ai")
            .withUsername("report_ai")
            .withPassword("report_ai_test_password");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            "minio/minio@sha256:fce0a90a37bb2887c850b68b61a0ac8ac02ea218b58eaea96bb7ca69eb503e20")
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server /data --console-address :9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            "redis@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    @Test
    void postgresMigrationsApplyToCleanDatabase() {
        Flyway flyway = postgresFlyway();
        flyway.migrate();

        // Keep this assertion explicit so a clean-database migration test
        // fails when a new migration is added without updating release evidence.
        assertEquals("20", flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.info().all().length >= 20);
    }

    @Test
    void expiredIdempotencyKeyCanBeReservedAgain() {
        postgresFlyway().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String tenantId = "tenant-expired-key";
        jdbc.update("DELETE FROM idempotency_record WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId, "Expired key test tenant");

        try {
            PostgresIdempotencyStore store = new PostgresIdempotencyStore(
                    new NamedParameterJdbcTemplate(dataSource));
            TenantId tenant = TenantId.of(tenantId);
            IdempotencyKey key = new IdempotencyKey("expired-key-1");
            IdempotencyRecord expired = new IdempotencyRecord(tenant, "document-upload", key,
                    "hash-old", "resource-old", IdempotencyRecord.Status.RESERVED,
                    Instant.now().minusSeconds(60));
            IdempotencyRecord replacement = new IdempotencyRecord(tenant, "document-upload", key,
                    "hash-new", "resource-new", IdempotencyRecord.Status.RESERVED,
                    Instant.now().plusSeconds(300));

            assertTrue(store.reserve(expired));
            assertTrue(store.reserve(replacement));
            assertEquals("hash-new", store.find(tenant, "document-upload", key).orElseThrow().requestHash());
        } finally {
            jdbc.update("DELETE FROM idempotency_record WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        }
    }

    @Test
    void taskManifestDigestIsDurableAndDatabaseImmutable() {
        postgresFlyway().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String tenantId = "tenant-manifest-immutable";
        String taskId = "manifest-task-immutable";
        jdbc.update("DELETE FROM report_task WHERE id = ?", taskId);
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId, "Manifest immutable tenant");
        try {
            jdbc.update("""
                    INSERT INTO report_task (id, tenant_id, document_object_key, status, version, created_at, updated_at)
                    VALUES (?, ?, ?, 'CREATED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, taskId, tenantId, "documents/manifest-task.docx");
            String first = "a".repeat(64);
            String second = "b".repeat(64);
            jdbc.update("UPDATE report_task SET manifest_digest = ? WHERE id = ?", first, taskId);
            assertEquals(first, jdbc.queryForObject("SELECT manifest_digest FROM report_task WHERE id = ?",
                    String.class, taskId));
            assertThrows(RuntimeException.class,
                    () -> jdbc.update("UPDATE report_task SET manifest_digest = ? WHERE id = ?", second, taskId));
            assertEquals(first, jdbc.queryForObject("SELECT manifest_digest FROM report_task WHERE id = ?",
                    String.class, taskId));
        } finally {
            jdbc.update("DELETE FROM report_task WHERE id = ?", taskId);
            jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        }
    }

    @Test
    void outboxEnforcesBusinessIdempotencyAndRecoversExpiredLeases() {
        postgresFlyway().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String tenantId = "tenant-outbox-test";
        jdbc.update("DELETE FROM scheduler_outbox WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId, "Outbox test tenant");

        try {
            PostgresSchedulerOutboxRepository repository = new PostgresSchedulerOutboxRepository(
                    new NamedParameterJdbcTemplate(dataSource));
            TenantId tenant = TenantId.of(tenantId);
            Instant now = Instant.now();
            QueuedTask first = queuedTask("outbox-1", tenant, "outbox-key-1", now);
            QueuedTask duplicate = queuedTask("outbox-2", tenant, "outbox-key-1", now.plusMillis(1));

            assertEquals("outbox-1", repository.enqueue(first).id());
            assertEquals("outbox-1", repository.enqueue(duplicate).id());
            SchedulerOutboxEntry claimed = repository.claimReady(now, "worker-a", Duration.ofSeconds(1), 1).getFirst();
            assertFalse(repository.markPublished(claimed.id(), "worker-b", now.plusMillis(1)));

            SchedulerOutboxEntry recovered = repository.claimReady(now.plusSeconds(2), "worker-b",
                    Duration.ofSeconds(1), 1).getFirst();
            assertEquals(claimed.id(), recovered.id());
            assertTrue(repository.markPublished(recovered.id(), "worker-b", now.plusSeconds(2)));
        } finally {
            jdbc.update("DELETE FROM scheduler_outbox WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        }
    }

    @Test
    void progressHistoryIsDurableTenantScopedAndStableForTimestampTies() {
        postgresFlyway().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        String ownerTenantId = "tenant-progress-owner";
        String otherTenantId = "tenant-progress-other";
        jdbc.update("DELETE FROM progress_event WHERE tenant_id IN (?, ?)", ownerTenantId, otherTenantId);
        jdbc.update("DELETE FROM tenant WHERE id IN (?, ?)", ownerTenantId, otherTenantId);
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ownerTenantId, "Progress owner tenant");
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                otherTenantId, "Progress other tenant");

        try {
            PostgresProgressEventHistoryRepository repository = new PostgresProgressEventHistoryRepository(
                    namedJdbc, new ObjectMapper());
            TenantId owner = TenantId.of(ownerTenantId);
            TenantId other = TenantId.of(otherTenantId);
            Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");
            PostgresSchedulerTaskStore taskStore = new PostgresSchedulerTaskStore(namedJdbc);
            assertTrue(taskStore.enqueue(queuedTask("progress-task", owner, "progress-key", sameTime)).created());
            assertTrue(taskStore.enqueue(queuedTask("progress-task-other", other, "progress-key-other", sameTime)).created());
            ProgressEvent first = event("event-a", "progress-task", sameTime, 10);
            ProgressEvent second = event("event-b", "progress-task", sameTime, 20);
            ProgressEvent third = event("event-c", "progress-task", sameTime.plusSeconds(1), 30);

            var secondEntry = repository.append(owner, second);
            var firstEntry = repository.append(owner, first);
            repository.append(owner, third);
            var duplicateEntry = repository.append(owner, new ProgressEvent("event-a", "progress-task", 99,
                    ProgressEventType.FAILED, 99, "duplicate must not replace", sameTime, Map.of()));
            repository.append(other, new ProgressEvent("event-a", "progress-task-other", 0,
                    ProgressEventType.PROGRESS, 1, "other tenant", sameTime, Map.of()));

            var firstPage = repository.listAfter(owner, "progress-task", null, 2);
            assertEquals(java.util.List.of("event-b", "event-a"),
                    firstPage.stream().map(entry -> entry.event().eventId()).toList());
            assertTrue(secondEntry.eventSequence() > 0);
            assertEquals(secondEntry.eventSequence(), secondEntry.event().eventSequence());
            assertEquals(firstEntry.eventSequence(), firstEntry.event().eventSequence());
            assertEquals(firstEntry.eventSequence(), duplicateEntry.eventSequence());
            assertEquals(firstEntry.eventSequence(), duplicateEntry.event().eventSequence());
            assertEquals(firstEntry.eventSequence(), firstPage.getLast().eventSequence());
            var secondPage = repository.listAfter(owner, "progress-task",
                    new ProgressEventCursor(firstPage.getLast().eventSequence()), 2);
            assertEquals(java.util.List.of("event-c"),
                    secondPage.stream().map(entry -> entry.event().eventId()).toList());
            assertEquals("task completed", repository.listAfter(owner, "progress-task", null, 10)
                    .getFirst().event().message());
            assertTrue(repository.listAfter(other, "progress-task", null, 10).stream()
                    .findAny().isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> repository.listAfter(owner, "progress-task", null, 102));

            // A fresh adapter instance proves that history survives the process boundary.
            PostgresProgressEventHistoryRepository restarted = new PostgresProgressEventHistoryRepository(
                    new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
            assertEquals(3, restarted.listAfter(owner, "progress-task", null, 10).size());
        } finally {
            jdbc.update("DELETE FROM progress_event WHERE tenant_id IN (?, ?)", ownerTenantId, otherTenantId);
            jdbc.update("DELETE FROM scheduler_task WHERE tenant_id IN (?, ?)", ownerTenantId, otherTenantId);
            jdbc.update("DELETE FROM tenant WHERE id IN (?, ?)", ownerTenantId, otherTenantId);
        }
    }

    @Test
    void schedulerTaskReadAndCancelAreTenantScoped() {
        postgresFlyway().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String ownerTenantId = "tenant-task-owner";
        String otherTenantId = "tenant-task-other";
        jdbc.update("DELETE FROM scheduler_task WHERE id = ?", "tenant-scoped-task");
        jdbc.update("DELETE FROM tenant WHERE id IN (?, ?)", ownerTenantId, otherTenantId);
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                ownerTenantId, "Task owner tenant");
        jdbc.update("INSERT INTO tenant (id, name, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                otherTenantId, "Other tenant");

        try {
            PostgresSchedulerTaskStore store = new PostgresSchedulerTaskStore(
                    new NamedParameterJdbcTemplate(dataSource));
            TenantId owner = TenantId.of(ownerTenantId);
            TenantId other = TenantId.of(otherTenantId);
            QueuedTask task = queuedTask("tenant-scoped-task", owner, "tenant-scoped-key", Instant.now());

            assertTrue(store.enqueue(task).created());
            assertTrue(store.findById(ownerTenantId, task.id()).isPresent());
            assertTrue(store.findById(otherTenantId, task.id()).isEmpty());
            assertFalse(store.cancel(task.id(), otherTenantId, Instant.now()));
            assertTrue(store.cancel(task.id(), ownerTenantId, Instant.now()));
        } finally {
            jdbc.update("DELETE FROM scheduler_task WHERE id = ?", "tenant-scoped-task");
            jdbc.update("DELETE FROM tenant WHERE id IN (?, ?)", ownerTenantId, otherTenantId);
        }
    }

    @Test
    void minioRoundTripPreservesTenantConfinedObjectIntegrity() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)
                .build();
        ObjectStorageProperties properties = new ObjectStorageProperties(
                endpoint, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET, "us-east-1", true, 1024 * 1024);
        MinioObjectStorage storage = new MinioObjectStorage(client, properties);
        storage.ensureBucketExists();
        TenantId tenant = TenantId.of("tenant-integration");
        String key = DocumentAssetKeyFactory.forDocument(tenant, "document-1", 1, "pixel.png");
        byte[] payload = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};
        String checksum = hexSha256(payload);
        StoredObject reference = new StoredObject(key, "image/png", payload.length, checksum);

        storage.put(reference, new ByteArrayInputStream(payload));
        TenantObjectStorageResolver resolver = new TenantObjectStorageResolver(storage);
        try (InputStream input = resolver.open(tenant, reference)) {
            assertArrayEquals(payload, input.readAllBytes());
        }
        resolver.delete(tenant, reference);
    }

    @Test
    void minioRoundTripPreservesTaskArtifactNamespace() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient client = MinioClient.builder().endpoint(endpoint)
                .credentials(MINIO_ACCESS_KEY, MINIO_SECRET_KEY).build();
        ObjectStorageProperties properties = new ObjectStorageProperties(
                endpoint, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET, "us-east-1", true, 1024 * 1024);
        MinioObjectStorage storage = new MinioObjectStorage(client, properties);
        storage.ensureBucketExists();
        TenantId tenant = TenantId.of("tenant-artifact-integration");
        String tenantHash = hexSha256(tenant.value().getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        String key = "tasks/" + tenantHash + "/task-1/artifacts/hash/plot.png";
        byte[] payload = new byte[]{1, 2, 3, 4};
        StoredObject reference = new StoredObject(key, "image/png", payload.length, hexSha256(payload));
        storage.put(reference, new ByteArrayInputStream(payload));
        TenantArtifactStorageResolver resolver = new TenantArtifactStorageResolver(storage);
        try (InputStream input = resolver.open(tenant, reference)) {
            assertArrayEquals(payload, input.readAllBytes());
        }
        resolver.delete(tenant, reference);
        assertThrows(IllegalArgumentException.class,
                () -> resolver.open(TenantId.of("tenant-other"), reference));
    }

    @Test
    void redisAcceptsHealthPing() throws Exception {
        try (Socket socket = new Socket(REDIS.getHost(), REDIS.getMappedPort(6379))) {
            socket.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            byte[] response = socket.getInputStream().readNBytes(7);
            assertEquals("+PONG\r\n", new String(response, StandardCharsets.US_ASCII));
        }
    }

    @Test
    void dockerRuntimeExecutesOnlyTheBoundedWorkspaceProgram() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-it-");
        Path workspace = Files.createDirectories(root.resolve("workspace-1"));
        Path output = Files.createDirectories(workspace.resolve("output"));
        Files.createDirectories(workspace.resolve("input"));
        Files.writeString(output.resolve("main.py"),
                "import os\nfrom pathlib import Path\n"
                        + "Path('generated.txt').write_text('report-ai-runtime-ok')\n"
                        + "status = Path('/proc/self/status').read_text()\n"
                        + "cap_eff = next(line.split(':', 1)[1].strip() for line in status.splitlines() if line.startswith('CapEff:'))\n"
                        + "no_new_privs = next(line.split(':', 1)[1].strip() for line in status.splitlines() if line.startswith('NoNewPrivs:'))\n"
                        + "Path('/tmp/reportai-tmp-check').write_text('ok')\n"
                        + "print(f'uid={os.getuid()} capeff={cap_eff} no_new_privs={no_new_privs}')\n",
                StandardCharsets.UTF_8);

        DockerRuntimeEngine engine = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), RuntimeConfiguration.productionDefaults(root));
        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest(
                "python", "main.py", Map.of(), new ExecutionPolicy(Duration.ofMinutes(2), 1024 * 1024, 1,
                512L * 1024 * 1024, false)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertTrue(result.stdout().contains("uid=65532"));
        assertTrue(result.stdout().contains("capeff=0000000000000000"));
        assertTrue(result.stdout().contains("no_new_privs=1"));
        assertEquals("report-ai-runtime-ok", Files.readString(output.resolve("generated.txt")));
    }

    @Test
    void dockerRuntimeInstallsFromThePinnedWheelhouseThenCleansTheEphemeralVolume() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-installer-it-");
        Path workspace = Files.createDirectories(root.resolve("workspace-installer"));
        Path output = Files.createDirectories(workspace.resolve("output"));
        Files.createDirectories(workspace.resolve("input"));
        Files.writeString(output.resolve("main.py"),
                "import numpy as np\n"
                        + "from pathlib import Path\n"
                        + "Path('generated.txt').write_text(str(np.arange(3).tolist()))\n",
                StandardCharsets.UTF_8);

        String image = "reportai/python-scientific@sha256:"
                + "6aafbe96a24e380848caebd5c370abb62cafc845bb03b656bcb770d7407ac425";
        DependencySpec numpy = new DependencySpec("numpy", "1.26.4",
                "675d61ffbfa78604709862923189bad94014bef562cc35cf61d3a07bba02a7ed");
        EnvironmentCatalog catalog = EnvironmentCatalog.of(
                java.util.List.of(new RuntimeImageProfile("python", image, "scientific")),
                java.util.List.of(numpy));
        RuntimeConfiguration configuration = new RuntimeConfiguration(
                root,
                java.util.Map.of("python", new LanguageRuntime("python", image,
                        entry -> java.util.List.of("python", entry))),
                100, 50L * 1024 * 1024, 2, 1024L * 1024 * 1024,
                Duration.ofMinutes(3), 4L * 1024 * 1024)
                .withCatalog(catalog);
        PythonDependencyRequest dependencies = new PythonDependencyRequest(
                java.util.List.of("numpy==1.26.4"), java.util.List.of(numpy));

        DockerRuntimeEngine engine = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), configuration);
        ExecutionResult result = engine.execute("workspace-installer", new ExecutionRequest(
                "python", "main.py", Map.of(), new ExecutionPolicy(Duration.ofMinutes(3),
                4L * 1024 * 1024, 1, 1024L * 1024 * 1024, false), dependencies));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status(),
                () -> "installer/execution failed: stdout=" + result.stdout() + " stderr=" + result.stderr());
        assertEquals("[0, 1, 2]", Files.readString(output.resolve("generated.txt")));
        assertFalse(Files.exists(workspace.resolve(".reportai-deps")),
                "dependency staging must not leak into the host workspace");
        assertFalse(Files.exists(workspace.resolve(".reportai-requirements.txt")),
                "installer requirements must not leak into the host workspace");
    }

    @Test
    void lifecycleKeepsInstallerAndExecutionAsSeparateRecoverableBoundaries() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-lifecycle-it-");
        Path workspace = Files.createDirectories(root.resolve("workspace-lifecycle"));
        Path output = Files.createDirectories(workspace.resolve("output"));
        Files.createDirectories(workspace.resolve("input"));
        Files.writeString(output.resolve("main.py"),
                "import numpy as np\n"
                        + "from pathlib import Path\n"
                        + "Path('lifecycle-result.txt').write_text(str(np.asarray([4, 5]).sum()))\n",
                StandardCharsets.UTF_8);

        String image = "reportai/python-scientific@sha256:"
                + "6aafbe96a24e380848caebd5c370abb62cafc845bb03b656bcb770d7407ac425";
        DependencySpec numpy = new DependencySpec("numpy", "1.26.4",
                "675d61ffbfa78604709862923189bad94014bef562cc35cf61d3a07bba02a7ed");
        EnvironmentCatalog catalog = EnvironmentCatalog.of(
                java.util.List.of(new RuntimeImageProfile("python", image, "scientific")),
                java.util.List.of(numpy));
        RuntimeConfiguration configuration = new RuntimeConfiguration(
                root,
                java.util.Map.of("python", new LanguageRuntime("python", image,
                        entry -> java.util.List.of("python", entry))),
                100, 50L * 1024 * 1024, 2, 1024L * 1024 * 1024,
                Duration.ofMinutes(3), 4L * 1024 * 1024).withCatalog(catalog);
        ExecutionPolicy policy = new ExecutionPolicy(Duration.ofMinutes(3),
                4L * 1024 * 1024, 1, 1024L * 1024 * 1024, false);
        ExecutionRequest request = new ExecutionRequest("python", "main.py", Map.of(), policy,
                new PythonDependencyRequest(java.util.List.of("numpy==1.26.4"), java.util.List.of(numpy)));

        RuntimeLifecycleEngine lifecycle = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), configuration);
        RuntimeInstallResult installed = lifecycle.install("workspace-lifecycle", request);
        assertTrue(installed.succeeded(), () -> "installer failed: " + installed.result().stderr());
        assertTrue(installed.dependencyVolume() != null && !installed.dependencyVolume().isBlank());
        try {
            ExecutionResult executed = lifecycle.executeInstalled("workspace-lifecycle", request,
                    installed.dependencyVolume());
            assertEquals(ExecutionResult.Status.SUCCEEDED, executed.status(),
                    () -> "execution failed: " + executed.stderr());
            assertEquals("9", Files.readString(output.resolve("lifecycle-result.txt")));
        } finally {
            lifecycle.cleanup("workspace-lifecycle", installed.dependencyVolume());
        }
        assertFalse(Files.exists(workspace.resolve(".reportai-deps")));
        assertFalse(Files.exists(workspace.resolve(".reportai-requirements.txt")));
    }

    @Test
    void dockerScientificRuntimeImportsTorchWithReadOnlyRootfsAndBoundedTmpfs() throws Exception {
        Path root = Files.createTempDirectory("reportai-scientific-runtime-it-");
        Path workspace = Files.createDirectories(root.resolve("workspace-scientific"));
        Path output = Files.createDirectories(workspace.resolve("output"));
        Files.createDirectories(workspace.resolve("input"));
        Files.writeString(output.resolve("main.py"),
                "import torch\n"
                        + "import torchvision\n"
                        + "from pathlib import Path\n"
                        + "value = torch.tensor([1, 2, 3]).sum().item()\n"
                        + "Path('scientific-import.txt').write_text("
                        + "f'{torch.__version__}|{torchvision.__version__}|{value}')\n"
                        + "print('scientific runtime imports ok')\n",
                StandardCharsets.UTF_8);

        String image = "reportai/python-scientific@sha256:"
                + "6aafbe96a24e380848caebd5c370abb62cafc845bb03b656bcb770d7407ac425";
        RuntimeConfiguration configuration = new RuntimeConfiguration(
                root,
                java.util.Map.of("python", new LanguageRuntime("python", image,
                        entry -> java.util.List.of("python", entry))),
                100, 50L * 1024 * 1024, 2, 1024L * 1024 * 1024,
                Duration.ofMinutes(3), 4L * 1024 * 1024);

        DockerRuntimeEngine engine = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), configuration);
        ExecutionResult result = engine.execute("workspace-scientific", new ExecutionRequest(
                "python", "main.py", Map.of(), new ExecutionPolicy(Duration.ofMinutes(3),
                4L * 1024 * 1024, 1, 1024L * 1024 * 1024, false)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status(),
                () -> "scientific runtime failed: stdout=" + result.stdout() + " stderr=" + result.stderr());
        assertTrue(result.stdout().contains("scientific runtime imports ok"));
        assertTrue(Files.readString(output.resolve("scientific-import.txt"))
                .matches("2\\.5\\.1\\+cpu\\|0\\.20\\.1\\+cpu\\|6"));
    }

    @Test
    void dockerRuntimeMountsInputReadOnlyAndOutputReadWrite() throws Exception {
        Path root = Files.createTempDirectory("reportai-input-mount-it-");
        Path workspace = Files.createDirectories(root.resolve("workspace-input"));
        Path output = Files.createDirectories(workspace.resolve("output"));
        Path input = Files.createDirectories(workspace.resolve("input"));
        Files.writeString(input.resolve("dataset.txt"), "verified-input", StandardCharsets.UTF_8);
        Files.writeString(output.resolve("main.py"),
                "from pathlib import Path\n"
                        + "value = Path('/workspace/input/dataset.txt').read_text()\n"
                        + "try:\n"
                        + "    Path('/workspace/input/should-not-exist').write_text('blocked')\n"
                        + "    write_state = 'write-unexpectedly-succeeded'\n"
                        + "except (PermissionError, OSError):\n"
                        + "    write_state = 'write-blocked'\n"
                        + "Path('input-result.txt').write_text(value + '|' + write_state)\n",
                StandardCharsets.UTF_8);

        RuntimeConfiguration configuration = RuntimeConfiguration.productionDefaults(root);
        DockerRuntimeEngine engine = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), configuration);
        ExecutionResult result = engine.execute("workspace-input", new ExecutionRequest(
                "python", "main.py", Map.of(), new ExecutionPolicy(Duration.ofMinutes(2),
                4L * 1024 * 1024, 1, 512L * 1024 * 1024, false)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status(),
                () -> "input mount execution failed: stdout=" + result.stdout() + " stderr=" + result.stderr());
        assertEquals("verified-input|write-blocked", Files.readString(output.resolve("input-result.txt")));
        assertFalse(Files.exists(input.resolve("should-not-exist")));
    }

    @Test
    void scientificRuntimeRunsGanAndCycleGanSmokeExperimentFromRealInputs() throws Exception {
        Path root = Files.createTempDirectory("reportai-gan-cycle-it-");
        Path workspace = Files.createDirectories(root.resolve("workspace-gan-cycle"));
        Path output = Files.createDirectories(workspace.resolve("output"));
        Path input = Files.createDirectories(workspace.resolve("input"));
        Files.writeString(input.resolve("domain_a.csv"), "0.0,0.0\n0.2,0.1\n0.4,0.2\n0.6,0.3\n",
                StandardCharsets.UTF_8);
        Files.writeString(input.resolve("domain_b.csv"), "1.0,1.0\n1.2,0.9\n1.4,0.8\n1.6,0.7\n",
                StandardCharsets.UTF_8);
        Files.writeString(output.resolve("main.py"),
                "import csv\n"
                        + "from pathlib import Path\n"
                        + "import torch\n"
                        + "from torch import nn\n"
                        + "import matplotlib; matplotlib.use('Agg')\n"
                        + "import matplotlib.pyplot as plt\n"
                        + "def load(name):\n"
                        + "    with open('/workspace/input/' + name, newline='') as f:\n"
                        + "        return torch.tensor([[float(x), float(y)] for x, y in csv.reader(f)])\n"
                        + "torch.manual_seed(7)\n"
                        + "a, b = load('domain_a.csv'), load('domain_b.csv')\n"
                        + "gan_g, gan_d = nn.Linear(2,2), nn.Linear(2,1)\n"
                        + "g_opt, d_opt = torch.optim.SGD(gan_g.parameters(), .05), torch.optim.SGD(gan_d.parameters(), .05)\n"
                        + "gan_losses = []\n"
                        + "for _ in range(3):\n"
                        + "    z = torch.randn_like(a); fake = gan_g(z)\n"
                        + "    d_opt.zero_grad(); d_loss = ((gan_d(a)-1)**2).mean() + (gan_d(fake.detach())**2).mean(); d_loss.backward(); d_opt.step()\n"
                        + "    g_opt.zero_grad(); g_loss = ((gan_d(gan_g(z))-1)**2).mean(); g_loss.backward(); g_opt.step(); gan_losses.append(float(g_loss))\n"
                        + "g_ab, g_ba = nn.Linear(2,2), nn.Linear(2,2)\n"
                        + "d_a, d_b = nn.Linear(2,1), nn.Linear(2,1)\n"
                        + "opt = torch.optim.SGD(list(g_ab.parameters())+list(g_ba.parameters())+list(d_a.parameters())+list(d_b.parameters()), .03)\n"
                        + "cycle_losses = []\n"
                        + "for _ in range(3):\n"
                        + "    fake_b, fake_a = g_ab(a), g_ba(b); rec_a, rec_b = g_ba(fake_b), g_ab(fake_a)\n"
                        + "    loss = ((d_b(fake_b)-1)**2).mean() + ((d_a(fake_a)-1)**2).mean() + (rec_a-a).abs().mean() + (rec_b-b).abs().mean()\n"
                        + "    opt.zero_grad(); loss.backward(); opt.step(); cycle_losses.append(float(loss))\n"
                        + "plt.plot(gan_losses, label='GAN generator'); plt.plot(cycle_losses, label='CycleGAN total'); plt.legend(); plt.savefig('/workspace/loss-curves.png')\n"
                        + "torch.save({'gan_generator': gan_g.state_dict(), 'cycle_g_ab': g_ab.state_dict()}, '/workspace/checkpoint.pt')\n"
                        + "plt.close(); plt.scatter(a[:,0], a[:,1], label='A'); plt.scatter(g_ab(a).detach()[:,0], g_ab(a).detach()[:,1], label='A to B'); plt.legend(); plt.savefig('/workspace/translation.png')\n"
                        + "print('gan_cycle_smoke_ok')\n",
                StandardCharsets.UTF_8);

        String image = "reportai/python-scientific@sha256:"
                + "6aafbe96a24e380848caebd5c370abb62cafc845bb03b656bcb770d7407ac425";
        RuntimeConfiguration configuration = new RuntimeConfiguration(
                root, java.util.Map.of("python", new LanguageRuntime("python", image,
                        entry -> java.util.List.of("python", entry))), 100, 50L * 1024 * 1024, 2,
                1024L * 1024 * 1024, Duration.ofMinutes(3), 8L * 1024 * 1024);
        DockerRuntimeEngine engine = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), configuration);
        ExecutionResult result = engine.execute("workspace-gan-cycle", new ExecutionRequest(
                "python", "main.py", Map.of(), new ExecutionPolicy(Duration.ofMinutes(3),
                8L * 1024 * 1024, 1, 1024L * 1024 * 1024, false)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status(),
                () -> "GAN/CycleGAN smoke failed: stdout=" + result.stdout() + " stderr=" + result.stderr());
        assertTrue(result.stdout().contains("gan_cycle_smoke_ok"));
        assertTrue(Files.size(output.resolve("loss-curves.png")) > 0);
        assertTrue(Files.size(output.resolve("translation.png")) > 0);
        assertTrue(Files.size(output.resolve("checkpoint.pt")) > 0);
    }

    private static String hexSha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Flyway postgresFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private static QueuedTask queuedTask(String id, TenantId tenant, String key, Instant createdAt) {
        return new QueuedTask(id, tenant, "report-task", "{}", key,
                new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(5)), createdAt);
    }

    private static ProgressEvent event(String id, String taskId, Instant occurredAt, int percent) {
        return new ProgressEvent(id, taskId, 0, ProgressEventType.PROGRESS, percent,
                "task completed", occurredAt, Map.of("phase", "execute", "error", "must be dropped"));
    }
}
