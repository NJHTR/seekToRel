package com.reportai.task;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportTaskRepositoryTest {
    @Test
    void recentListingUsesTenantScopedNewestFirstKeysetOrdering() {
        InMemoryReportTaskRepository repository = new InMemoryReportTaskRepository();
        TenantId owner = TenantId.of("tenant-owner");
        TenantId other = TenantId.of("tenant-other");
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        repository.save(task("task-a", owner, created));
        repository.save(task("task-b", owner, created));
        repository.save(task("task-c", owner, created.plusSeconds(1)));
        repository.save(task("task-x", other, created.plusSeconds(2)));

        List<ReportTask> first = repository.listRecent(owner, null, 2);
        assertEquals(List.of("task-c", "task-b"), first.stream().map(ReportTask::id).toList());
        List<ReportTask> second = repository.listRecent(owner,
                new ReportTaskCursor(first.getLast().createdAt(), first.getLast().id()), 2);
        assertEquals(List.of("task-a"), second.stream().map(ReportTask::id).toList());
        assertThrows(IllegalArgumentException.class, () -> repository.listRecent(owner, null, 102));
        assertEquals(List.of("task-x"), repository.listRecent(other, null, 2)
                .stream().map(ReportTask::id).toList());
    }

    @Test
    void manifestDigestCanBeLockedOnceAndCannotBeReplaced() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ReportTask task = task("manifest-task", TenantId.of("tenant-owner"), now);
        String digest = "a".repeat(64);
        ReportTask locked = task.lockManifestDigest(digest);

        assertEquals(digest, locked.manifestDigest());
        assertEquals(task.version() + 1, locked.version());
        assertEquals(locked, locked.lockManifestDigest(digest));
        assertThrows(IllegalStateException.class,
                () -> locked.lockManifestDigest("b".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> task.lockManifestDigest("not-a-digest"));
    }

    private static ReportTask task(String id, TenantId tenant, Instant createdAt) {
        return new ReportTask(id, tenant, "documents/" + id + ".docx", ReportTaskStatus.CREATED,
                0, createdAt, createdAt);
    }
}
