package com.reportai.review.workflow;

import com.reportai.common.contract.CommandId;
import com.reportai.common.contract.EvaluationId;
import com.reportai.common.contract.EventId;

import java.time.Instant;
import java.util.Objects;

public record ReviewEvent(EventId eventId, CommandId commandId, String idempotencyKey, String caseId,
                          ReviewAction action, ReviewState from, ReviewState to, EvaluationId evaluationId,
                          long version, Instant occurredAt) {
    public ReviewEvent {
        eventId = Objects.requireNonNull(eventId, "eventId");
        commandId = Objects.requireNonNull(commandId, "commandId");
        idempotencyKey = text(idempotencyKey, "idempotencyKey");
        caseId = text(caseId, "caseId");
        action = Objects.requireNonNull(action, "action");
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        if (version < 1) throw new IllegalArgumentException("event version must be positive");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        String n = value.strip();
        if (n.isBlank() || n.length() > 128 || n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid");
        return n;
    }
}
