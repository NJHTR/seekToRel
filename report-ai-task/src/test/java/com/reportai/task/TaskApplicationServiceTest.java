package com.reportai.task;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskApplicationServiceTest {
    private static final TenantId TENANT_A = TenantId.of("tenant-a");
    private static final TenantId TENANT_B = TenantId.of("tenant-b");
    private static final IdempotencyKey KEY = new IdempotencyKey("task-key-1");

    @Test
    void replayReturnsTheOriginalTaskWithoutCreatingAnotherResource() {
        InMemoryReportTaskRepository repository = new InMemoryReportTaskRepository();
        TaskApplicationService service = service(repository, new InMemoryIdempotencyStore(), "task-1");
        CreateReportTaskCommand command = command(TENANT_A, "documents/a.docx", KEY);

        ReportTask first = service.create(command);
        ReportTask replay = service.create(command);

        assertEquals(first, replay);
        assertEquals(first.id(), replay.id());
        assertEquals("documents/a.docx", replay.documentObjectKey());
    }

    @Test
    void reusingAKeyWithDifferentDocumentIsRejectedBeforeRepositoryLookup() {
        InMemoryReportTaskRepository repository = new InMemoryReportTaskRepository();
        TaskApplicationService service = service(repository, new InMemoryIdempotencyStore(), "task-2");
        service.create(command(TENANT_A, "documents/a.docx", KEY));

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.create(command(TENANT_A, "documents/b.docx", KEY)));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.getErrorCode());
        assertEquals(Optional.of("documents/a.docx"), repository.findById(TENANT_A, "task-2")
                .map(ReportTask::documentObjectKey));
    }

    @Test
    void sameKeyIsScopedToTenant() {
        InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
        InMemoryReportTaskRepository repository = new InMemoryReportTaskRepository();
        TaskApplicationService service = service(repository, idempotency, "task-3", "task-4");

        ReportTask tenantATask = service.create(command(TENANT_A, "documents/a.docx", KEY));
        ReportTask tenantBTask = service.create(command(TENANT_B, "documents/a.docx", KEY));

        assertNotEquals(tenantATask.id(), tenantBTask.id());
        assertEquals(TENANT_A, tenantATask.tenantId());
        assertEquals(TENANT_B, tenantBTask.tenantId());
    }

    @Test
    void repositoryFailureReleasesReservationSoARetryCanSucceed() {
        FailingOnceRepository repository = new FailingOnceRepository();
        InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
        TaskApplicationService service = service(repository, idempotency, "task-5", "task-6");
        CreateReportTaskCommand command = command(TENANT_A, "documents/a.docx", KEY);

        assertThrows(IllegalStateException.class, () -> service.create(command));
        ReportTask retry = service.create(command);

        assertEquals("task-6", retry.id());
        assertEquals(Optional.of(retry), repository.findById(TENANT_A, retry.id()));
    }

    private static CreateReportTaskCommand command(TenantId tenant, String objectKey, IdempotencyKey key) {
        return new CreateReportTaskCommand(tenant, objectKey, key);
    }

    private static TaskApplicationService service(ReportTaskRepository repository,
                                                   InMemoryIdempotencyStore idempotency,
                                                   String... ids) {
        AtomicInteger index = new AtomicInteger();
        IdGenerator generator = () -> ids[Math.min(index.getAndIncrement(), ids.length - 1)];
        return new TaskApplicationService(repository, idempotency, generator);
    }

    private static final class FailingOnceRepository implements ReportTaskRepository {
        private final InMemoryReportTaskRepository delegate = new InMemoryReportTaskRepository();
        private boolean fail = true;

        @Override
        public synchronized ReportTask save(ReportTask task) {
            if (fail) {
                fail = false;
                throw new IllegalStateException("simulated persistence failure");
            }
            return delegate.save(task);
        }

        @Override
        public boolean update(ReportTask task, long expectedVersion) {
            return delegate.update(task, expectedVersion);
        }

        @Override
        public Optional<ReportTask> findById(TenantId tenantId, String taskId) {
            return delegate.findById(tenantId, taskId);
        }
    }
}
