package com.reportai.integration;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.DocumentAssetKeyFactory;
import com.reportai.runtime.DockerJavaSandboxClient;
import com.reportai.runtime.DockerRuntimeEngine;
import com.reportai.runtime.ExecutionPolicy;
import com.reportai.runtime.ExecutionRequest;
import com.reportai.runtime.ExecutionResult;
import com.reportai.runtime.RuntimeConfiguration;
import com.reportai.storage.StoredObject;
import com.reportai.storage.objectstore.MinioObjectStorage;
import com.reportai.storage.objectstore.ObjectStorageProperties;
import com.reportai.storage.objectstore.TenantObjectStorageResolver;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.flywaydb.core.Flyway;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("report_ai")
            .withUsername("report_ai")
            .withPassword("report_ai_test_password");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-06-29T01-20-47Z")
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server /data --console-address :9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    @Test
    void postgresMigrationsApplyToCleanDatabase() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.migrate();

        assertEquals("14", flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.info().all().length >= 14);
    }

    @Test
    void minioRoundTripPreservesTenantConfinedObjectIntegrity() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)
                .build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(MINIO_BUCKET).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(MINIO_BUCKET).build());
        }

        ObjectStorageProperties properties = new ObjectStorageProperties(
                endpoint, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET, "us-east-1", true, 1024 * 1024);
        MinioObjectStorage storage = new MinioObjectStorage(client, properties);
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
        Files.writeString(workspace.resolve("main.py"), "print('report-ai-runtime-ok')\n", StandardCharsets.UTF_8);

        DockerRuntimeEngine engine = new DockerRuntimeEngine(
                DockerJavaSandboxClient.connectFromEnvironment(), RuntimeConfiguration.productionDefaults(root));
        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest(
                "python", "main.py", Map.of(), new ExecutionPolicy(Duration.ofMinutes(2), 1024 * 1024, 1,
                512L * 1024 * 1024, false)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertTrue(result.stdout().contains("report-ai-runtime-ok"));
    }

    private static String hexSha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
