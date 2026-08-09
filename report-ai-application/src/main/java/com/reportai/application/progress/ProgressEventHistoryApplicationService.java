package com.reportai.application.progress;

import com.reportai.api.report.ProgressEventHistoryResponse;
import com.reportai.api.report.ProgressEventResponse;
import com.reportai.application.task.TaskHttpApplicationService;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.ProgressEvent;
import com.reportai.scheduler.ProgressEventCursor;
import com.reportai.scheduler.ProgressEventHistoryEntry;
import com.reportai.scheduler.ProgressEventHistoryRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Tenant-authorized application boundary for durable task observability history. */
@Service
public final class ProgressEventHistoryApplicationService {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;
    private static final String CURSOR_VERSION = "v1";
    private static final Base64.Encoder CURSOR_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder CURSOR_DECODER = Base64.getUrlDecoder();

    private final TaskHttpApplicationService tasks;
    private final ProgressEventHistoryRepository history;

    public ProgressEventHistoryApplicationService(TaskHttpApplicationService tasks,
                                                  ProgressEventHistoryRepository history) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.history = Objects.requireNonNull(history, "history");
    }

    public ProgressEventHistoryResponse list(String taskId, String after, String limitValue) {
        TenantId tenantId = tasks.requireTenantForRead(taskId);
        int limit = parseLimit(limitValue);
        ProgressEventCursor cursor = decodeCursor(after, taskId, tenantId);
        List<ProgressEventHistoryEntry> rows = history.listAfter(tenantId, taskId, cursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ProgressEventHistoryEntry> page = hasMore ? rows.subList(0, limit) : rows;
        List<ProgressEventResponse> events = new ArrayList<>(page.size());
        for (ProgressEventHistoryEntry entry : page) {
            events.add(toResponse(entry));
        }
        String nextCursor = hasMore && !page.isEmpty()
                ? encodeCursor(tenantId, taskId, page.get(page.size() - 1).eventSequence()) : null;
        return new ProgressEventHistoryResponse(events, nextCursor, hasMore);
    }

    private static int parseLimit(String raw) {
        if (raw == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (!raw.matches("[0-9]{1,3}")) {
            throw invalid("limit is invalid");
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException failure) {
            throw invalid("limit is invalid");
        }
        if (value < 1 || value > MAX_PAGE_SIZE) {
            throw invalid("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return value;
    }

    private static ProgressEventCursor decodeCursor(String raw, String taskId, TenantId tenantId) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank() || raw.length() > 512 || !raw.matches("[A-Za-z0-9_-]+")) {
            throw invalid("after cursor is invalid");
        }
        final String payload;
        try {
            payload = new String(CURSOR_DECODER.decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw invalid("after cursor is invalid");
        }
        String[] parts = payload.split("\\n", -1);
        if (parts.length != 4 || !CURSOR_VERSION.equals(parts[0]) || !tenantId.value().equals(parts[1])
                || !taskId.equals(parts[2]) || parts[3].isBlank() || !parts[3].matches("[1-9][0-9]{0,18}")) {
            throw invalid("after cursor is invalid");
        }
        try {
            return new ProgressEventCursor(Long.parseLong(parts[3]));
        } catch (RuntimeException failure) {
            throw invalid("after cursor is invalid");
        }
    }

    private static String encodeCursor(TenantId tenantId, String taskId, long eventSequence) {
        String payload = CURSOR_VERSION + "\n" + tenantId.value() + "\n" + taskId + "\n" + eventSequence;
        return CURSOR_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static ProgressEventResponse toResponse(ProgressEventHistoryEntry entry) {
        ProgressEvent event = entry.event();
        return new ProgressEventResponse(entry.eventSequence(), event.eventId(), event.taskId(), event.attempt(),
                event.type().name(), event.percent(), event.message(), event.occurredAt(),
                event.attributes());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
