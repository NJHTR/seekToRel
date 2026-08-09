package com.reportai.application.document;

import com.reportai.api.document.UploadDocumentResponse;
import com.reportai.api.report.CreateTaskRequest;
import com.reportai.api.report.TaskResponse;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.UuidIdGenerator;
import com.reportai.common.idempotency.IdempotencyStore;
import com.reportai.common.tenant.TenantContext;
import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.parser.DocumentParserPort;
import com.reportai.parser.ParserRequest;
import com.reportai.parser.ParserResult;
import com.reportai.parser.poi.PoiDocxParser;
import com.reportai.storage.DocumentRepository;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.task.InMemoryIdempotencyStore;
import com.reportai.task.InMemoryReportTaskRepository;
import com.reportai.task.TaskApplicationService;
import com.reportai.scheduler.InMemorySchedulerOutboxRepository;
import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.application.task.TaskHttpApplicationService;
import com.reportai.review.CompletenessReviewEngine;
import com.reportai.word.DefaultWordReleaseService;
import com.reportai.word.PoiWordBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentUploadApplicationServiceTest {
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Test
    void sameTenantReplayReturnsTheOriginalResourceWithoutDuplicatingStorage() {
        Fixture fixture = new Fixture(TenantId.of("tenant-a"));
        MultipartFile file = file("report.docx", new byte[]{'P', 'K', 3, 4, 1, 2, 3});

        UploadDocumentResponse first = fixture.service().upload(file, "upload-key-1");
        UploadDocumentResponse replay = fixture.service().upload(file, "upload-key-1");

        assertEquals("PARSED", first.status());
        assertEquals(first.documentId(), replay.documentId());
        assertEquals("PARSED", replay.status());
        assertEquals(1, fixture.storage().objects().size());
        assertEquals(1, fixture.documents().saved().size());
    }

    @Test
    void reusingAKeyWithDifferentContentIsRejected() {
        Fixture fixture = new Fixture(TenantId.of("tenant-a"));
        fixture.service().upload(file("report.docx", new byte[]{'P', 'K', 3, 4, 1}), "upload-key-2");

        BusinessException failure = assertThrows(BusinessException.class,
                () -> fixture.service().upload(file("report.docx", new byte[]{'P', 'K', 3, 4, 9}),
                        "upload-key-2"));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.getErrorCode());
        assertEquals(1, fixture.storage().objects().size());
    }

    @Test
    void idempotencyKeysAreTenantScoped() {
        InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryIntegrityRepository integrity = new InMemoryIntegrityRepository();
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        DocumentUploadApplicationService tenantA = createService(
                TenantId.of("tenant-a"), idempotency, documents, integrity, storage);
        DocumentUploadApplicationService tenantB = createService(
                TenantId.of("tenant-b"), idempotency, documents, integrity, storage);

        UploadDocumentResponse first = tenantA.upload(file("report.docx", new byte[]{'P', 'K', 3, 4, 1}), "shared-key");
        UploadDocumentResponse second = tenantB.upload(file("report.docx", new byte[]{'P', 'K', 3, 4, 1}), "shared-key");

        assertNotEquals(first.documentId(), second.documentId());
        assertEquals(2, documents.saved().size());
        assertEquals(2, storage.objects().size());
    }

    @Test
    void concurrentSameKeyRequestsConvergeOnOneResource() throws Exception {
        Fixture fixture = new Fixture(TenantId.of("tenant-concurrent"));
        MultipartFile file = file("report.docx", new byte[]{'P', 'K', 3, 4, 8, 9});
        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            List<Future<UploadDocumentResponse>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return fixture.service().upload(file, "concurrent-key");
                }));
            }
            ready.await();
            start.countDown();
            List<UploadDocumentResponse> responses = new ArrayList<>();
            for (Future<UploadDocumentResponse> future : futures) {
                responses.add(future.get());
            }
            assertEquals(1, responses.stream().map(UploadDocumentResponse::documentId).distinct().count());
            assertEquals(1, fixture.storage().objects().size());
            assertEquals(1, fixture.documents().saved().size());
        }
    }

    @Test
    void unsafeFileNamesAreRejectedBeforeIdempotencyReservation() {
        Fixture fixture = new Fixture(TenantId.of("tenant-a"));

        assertThrows(BusinessException.class,
                () -> fixture.service().upload(file("../report.docx", new byte[]{'P', 'K', 3, 4}),
                        "upload-key-3"));
        assertEquals(0, fixture.storage().objects().size());
    }

    @Test
    void realDocxParserFeedsCanonicalDocumentAndObjectStorage() throws Exception {
        InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryIntegrityRepository integrity = new InMemoryIntegrityRepository();
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        DocumentUploadApplicationService service = createService(TenantId.of("tenant-parser"), idempotency,
                documents, integrity, storage, List.of(new PoiDocxParser()));
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Experiment {{title}} question?");
            document.write(output);
            docx = output.toByteArray();
        }

        UploadDocumentResponse response = service.upload(file("experiment.docx", docx), "parser-key");

        assertEquals("PARSED", response.status());
        assertEquals(1, documents.saved().size());
        assertEquals(1, storage.objects().size());
        assertTrue(documents.saved().iterator().next().blocks().size() >= 1);
    }

    @Test
    void uploadTaskReviewAndExportPipelineProducesDocx() {
        TenantId tenant = TenantId.of("tenant-pipeline");
        Set<Permission> permissions = Set.of(Permission.DOCUMENT_WRITE, Permission.TASK_CREATE,
                Permission.EXPORT_READ);
        Fixture fixture = new Fixture(tenant, permissions);
        UploadDocumentResponse uploaded = fixture.service().upload(
                file("pipeline.docx", new byte[]{'P', 'K', 3, 4, 7, 8}), "pipeline-upload");

        InMemoryReportTaskRepository tasks = new InMemoryReportTaskRepository();
        InMemorySchedulerOutboxRepository outbox = new InMemorySchedulerOutboxRepository();
        ReportAiSchedulerProperties schedulerProperties = new ReportAiSchedulerProperties();
        TaskHttpApplicationService taskService = new TaskHttpApplicationService(
                new TaskApplicationService(tasks, fixture.idempotency(), new UuidIdGenerator()), tasks,
                fixture.documents(), fixture.integrity(), fixture.authorization(), new ObjectMapper(),
                outbox, schedulerProperties);
        TenantContext.set(tenant);
        try {
            TaskResponse task = taskService.create(new CreateTaskRequest(uploaded.documentId(), null), "pipeline-task");
            assertEquals("CREATED", task.status());
            assertEquals(1, outbox.claimReady(java.time.Instant.now(), "test", java.time.Duration.ofMinutes(1), 1).size());

            DocumentExportApplicationService exportService = new DocumentExportApplicationService(
                    fixture.authorization(), fixture.documents(),
                    new DefaultWordReleaseService(new CompletenessReviewEngine(), new PoiWordBuilder()));
            byte[] exported = exportService.export(uploaded.documentId());
            assertTrue(exported.length > 4);
            assertEquals('P', exported[0]);
            assertEquals('K', exported[1]);
        } finally {
            TenantContext.clear();
        }
    }

    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, DOCX_MEDIA_TYPE, bytes);
    }

    private static DocumentUploadApplicationService createService(
            TenantId tenant,
            IdempotencyStore idempotency,
            InMemoryDocumentRepository documents,
            InMemoryIntegrityRepository integrity,
            InMemoryObjectStorage storage) {
        return createService(tenant, idempotency, documents, integrity, storage,
                List.of(new DeterministicParser()));
    }

    private static DocumentUploadApplicationService createService(
            TenantId tenant,
            IdempotencyStore idempotency,
            InMemoryDocumentRepository documents,
            InMemoryIntegrityRepository integrity,
            InMemoryObjectStorage storage,
            List<DocumentParserPort> parsers) {
        return createService(new FixedAuthorization(tenant), idempotency, documents, integrity, storage, parsers);
    }

    private static DocumentUploadApplicationService createService(
            FixedAuthorization authorization,
            IdempotencyStore idempotency,
            InMemoryDocumentRepository documents,
            InMemoryIntegrityRepository integrity,
            InMemoryObjectStorage storage,
            List<DocumentParserPort> parsers) {
        return new DocumentUploadApplicationService(
                authorization, idempotency, documents, integrity,
                provider(storage), parsers, new UuidIdGenerator(), properties());
    }

    private static ObjectProvider<ObjectStorage> provider(ObjectStorage storage) {
        return new ObjectProvider<>() {
            @Override
            public ObjectStorage getObject() {
                return storage;
            }
        };
    }

    private static com.reportai.application.config.properties.ReportAiUploadProperties properties() {
        var properties = new com.reportai.application.config.properties.ReportAiUploadProperties();
        properties.setMaxBytes(1024 * 1024);
        properties.setMaxFileNameLength(255);
        properties.setAllowedMediaTypes(Set.of(DOCX_MEDIA_TYPE));
        properties.setAllowedExtensions(Set.of(".docx"));
        return properties;
    }

    private static final class Fixture {
        private final InMemoryDocumentRepository documents;
        private final InMemoryIntegrityRepository integrity;
        private final InMemoryObjectStorage storage;
        private final InMemoryIdempotencyStore idempotency;
        private final FixedAuthorization authorization;
        private final DocumentUploadApplicationService service;

        private Fixture(TenantId tenant) {
            this(tenant, Set.of(Permission.DOCUMENT_WRITE));
        }

        private Fixture(TenantId tenant, Set<Permission> permissions) {
            documents = new InMemoryDocumentRepository();
            integrity = new InMemoryIntegrityRepository();
            storage = new InMemoryObjectStorage();
            idempotency = new InMemoryIdempotencyStore();
            authorization = new FixedAuthorization(tenant, permissions);
            service = createService(authorization, idempotency, documents, integrity, storage,
                    List.of(new DeterministicParser()));
        }

        private InMemoryDocumentRepository documents() {
            return documents;
        }

        private InMemoryIntegrityRepository integrity() {
            return integrity;
        }

        private InMemoryObjectStorage storage() {
            return storage;
        }

        private DocumentUploadApplicationService service() {
            return service;
        }

        private InMemoryIdempotencyStore idempotency() {
            return idempotency;
        }

        private FixedAuthorization authorization() {
            return authorization;
        }
    }

    private static final class FixedAuthorization implements AuthorizationPort {
        private final TenantPrincipal principal;

        private FixedAuthorization(TenantId tenant) {
            this(tenant, Set.of(Permission.DOCUMENT_WRITE));
        }

        private FixedAuthorization(TenantId tenant, Set<Permission> permissions) {
            principal = TenantPrincipal.authenticated("subject-1", tenant,
                    permissions, Instant.now().plusSeconds(3600), "token-1");
        }

        @Override
        public TenantPrincipal requireAuthenticated() {
            return principal;
        }

        @Override
        public void requirePermission(Permission permission) {
            if (!principal.hasPermission(permission)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "permission denied");
            }
        }

        @Override
        public void requireAnyPermission(Iterable<Permission> permissions) {
            for (Permission permission : permissions) {
                if (principal.hasPermission(permission)) {
                    return;
                }
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "permission denied");
        }

        @Override
        public void requireTenant(TenantId targetTenantId) {
            if (!principal.tenantId().equals(targetTenantId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "tenant denied");
            }
        }

        @Override
        public boolean isAllowed(Permission permission) {
            return principal.hasPermission(permission);
        }
    }

    private static final class DeterministicParser implements DocumentParserPort {
        @Override
        public boolean supports(String mediaType, String fileName) {
            return DOCX_MEDIA_TYPE.equals(mediaType) && fileName.endsWith(".docx");
        }

        @Override
        public ParserResult parse(ParserRequest request) {
            return new ParserResult(new Document(request.documentId(), request.tenantId(),
                    request.sourceObjectKey(), request.mediaType(), List.of(
                            new DocumentBlock(BlockType.TITLE, "Experiment", List.of(), null),
                            new DocumentBlock(BlockType.PARAGRAPH, "Summary", List.of(), null)),
                    List.of(), List.of()),
                    request.contentLength(), List.of());
        }
    }

    private static final class InMemoryDocumentRepository implements DocumentRepository {
        private final Map<String, Document> values = new ConcurrentHashMap<>();

        @Override
        public Document save(Document document) {
            values.put(document.tenantId().value() + ':' + document.id(), document);
            return document;
        }

        @Override
        public java.util.Optional<Document> findById(TenantId tenantId, String documentId) {
            return java.util.Optional.ofNullable(values.get(tenantId.value() + ':' + documentId));
        }

        private Collection<Document> saved() {
            return values.values();
        }
    }

    private static final class InMemoryIntegrityRepository implements ObjectIntegrityRepository {
        private final List<ObjectIntegrity> values = new ArrayList<>();

        @Override
        public synchronized ObjectIntegrity save(ObjectIntegrity integrity) {
            values.add(integrity);
            return integrity;
        }

        @Override
        public synchronized List<ObjectIntegrity> findByOwner(TenantId tenantId, String ownerType, String ownerId) {
            return values.stream().filter(value -> value.tenantId().equals(tenantId)
                    && value.ownerType().equals(ownerType) && value.ownerId().equals(ownerId)).toList();
        }

        @Override
        public synchronized void deleteByOwner(TenantId tenantId, String ownerType, String ownerId) {
            values.removeIf(value -> value.tenantId().equals(tenantId)
                    && value.ownerType().equals(ownerType) && value.ownerId().equals(ownerId));
        }
    }

    private static final class InMemoryObjectStorage implements ObjectStorage {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();

        @Override
        public String put(String objectKey, InputStream data, long contentLength, String contentType) {
            try {
                byte[] bytes = data.readAllBytes();
                if (bytes.length != contentLength) {
                    throw new IllegalArgumentException("unexpected object length");
                }
                values.put(objectKey, bytes);
                return objectKey;
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public InputStream open(String objectKey) {
            byte[] value = values.get(objectKey);
            if (value == null) {
                throw new IllegalArgumentException("object not found");
            }
            return new ByteArrayInputStream(value);
        }

        @Override
        public void delete(String objectKey) {
            values.remove(objectKey);
        }

        private Map<String, byte[]> objects() {
            return values;
        }
    }
}
