package com.reportai.agent;

import java.util.List;

/**
 * Maintenance read port for bounded, cross-tenant conversation compaction.
 * Implementations must return only candidates whose history has advanced past
 * the durable snapshot (or conversations without a snapshot).
 */
@FunctionalInterface
public interface ConversationCompactionCandidateRepository {
    List<ConversationCompactionCandidate> findCandidates(int limit);
}
