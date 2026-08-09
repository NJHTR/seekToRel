package com.reportai.storage;

import java.time.Instant;
import java.util.List;

/** Storage-side queries used by the bounded integrity reconciliation job. */
public interface ObjectIntegrityReconciliationRepository {
    List<ObjectIntegrity> findForVerification(int limit);

    /** Finds ownerless metadata old enough that deleting its physical object is safe. */
    default List<ObjectIntegrity> findOrphanedMetadata(int limit, Instant olderThan) {
        return List.of();
    }

    /** Deletes metadata only after the corresponding physical object was removed successfully. */
    default int deleteMetadataByIds(List<String> ids) {
        return 0;
    }

    void markVerified(String id, Instant verifiedAt);

    void markVerificationFailed(String id, Instant failedAt, String error);
}
