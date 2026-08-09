package com.reportai.application.progress;

import com.reportai.application.task.TaskHttpApplicationService;
import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventCursor;
import com.reportai.scheduler.ProgressEventHistoryRepository;
import com.reportai.scheduler.ProgressEventHistoryEntry;
import com.reportai.scheduler.ProgressEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressEventHistoryApplicationServiceTest {
    private static final TenantId TENANT = TenantId.of("tenant-a");

    @Test
    void returnsBoundedKeysetPagesAndOpaqueTaskBoundCursor() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        InMemoryHistory history = new InMemoryHistory(List.of(
                event("event-a", "task-1", timestamp),
                event("event-b", "task-1", timestamp),
                event("event-c", "task-1", timestamp.plusSeconds(1))));
        ProgressEventHistoryApplicationService service = new ProgressEventHistoryApplicationService(
                new AuthorizedTasks(), history);

        var first = service.list("task-1", null, "2");
        assertEquals(List.of("event-a", "event-b"), first.events().stream()
                .map(item -> item.eventId()).toList());
        assertTrue(first.hasMore());
        assertTrue(first.nextCursor() != null && !first.nextCursor().contains("task-1"));

        var second = service.list("task-1", first.nextCursor(), "2");
        assertEquals(List.of("event-c"), second.events().stream()
                .map(item -> item.eventId()).toList());
        assertFalse(second.hasMore());
        assertEquals(null, second.nextCursor());
    }

    @Test
    void rejectsInvalidBoundsAndCursorFromAnotherTask() {
        Instant now = Instant.now();
        InMemoryHistory history = new InMemoryHistory(List.of(
                event("event-a", "task-1", now), event("event-b", "task-1", now.plusMillis(1))));
        ProgressEventHistoryApplicationService service = new ProgressEventHistoryApplicationService(
                new AuthorizedTasks(), history);
        assertThrows(RuntimeException.class, () -> service.list("task-1", null, "0"));
        assertThrows(RuntimeException.class, () -> service.list("task-1", null, "101"));
        var page = service.list("task-1", null, "1");
        assertThrows(RuntimeException.class, () -> service.list("task-2", page.nextCursor(), "1"));
    }

    @Test
    void passesAuthenticatedTenantToHistoryRepository() {
        InMemoryHistory history = new InMemoryHistory(List.of());
        ProgressEventHistoryApplicationService service = new ProgressEventHistoryApplicationService(
                new AuthorizedTasks(), history);
        service.list("task-1", null, null);
        assertEquals(TENANT, history.lastTenant);
    }

    private static ProgressEvent event(String id, String taskId, Instant timestamp) {
        return new ProgressEvent(id, taskId, 0, ProgressEventType.PROGRESS, 10,
                "step", timestamp, Map.of("phase", "execute"));
    }

    private static final class AuthorizedTasks extends TaskHttpApplicationService {
        private AuthorizedTasks() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public TenantId requireTenantForRead(String taskId) {
            if (taskId == null || !taskId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("taskId is invalid");
            }
            return TENANT;
        }
    }

    private static final class InMemoryHistory implements ProgressEventHistoryRepository {
        private final List<ProgressEvent> events;
        private TenantId lastTenant;

        private InMemoryHistory(List<ProgressEvent> events) {
            this.events = new ArrayList<>(events);
        }

        @Override
        public ProgressEventHistoryEntry append(TenantId tenantId, ProgressEvent event) {
            events.add(event);
            return new ProgressEventHistoryEntry(events.size(), event);
        }

        @Override
        public List<ProgressEventHistoryEntry> listAfter(TenantId tenantId, String taskId,
                                                         ProgressEventCursor after, int limit) {
            lastTenant = tenantId;
            List<ProgressEvent> sorted = events.stream()
                    .filter(event -> event.taskId().equals(taskId))
                    .sorted(java.util.Comparator.comparing(ProgressEvent::occurredAt)
                            .thenComparing(ProgressEvent::eventId))
                    .toList();
            return java.util.stream.IntStream.range(0, sorted.size())
                    .mapToObj(index -> new ProgressEventHistoryEntry(index + 1L, sorted.get(index)))
                    .filter(entry -> after == null || entry.eventSequence() > after.eventSequence())
                    .limit(limit)
                    .toList();
        }
    }
}
