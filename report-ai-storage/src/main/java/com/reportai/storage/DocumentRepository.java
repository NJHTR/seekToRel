package com.reportai.storage;

import com.reportai.document.model.Document;
import com.reportai.common.tenant.TenantId;

import java.util.Optional;

public interface DocumentRepository {
    Document save(Document document);

    Optional<Document> findById(TenantId tenantId, String documentId);
}
