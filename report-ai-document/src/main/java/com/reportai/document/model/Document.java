package com.reportai.document.model;

import com.reportai.common.tenant.TenantId;

import java.util.List;
import java.util.Objects;

public record Document(
        String id,
        TenantId tenantId,
        String sourceObjectKey,
        String mediaType,
        List<DocumentBlock> blocks,
        List<Placeholder> placeholders,
        List<Question> questions,
        List<DocumentAsset> assets) {

    public Document {
        id = Objects.requireNonNull(id, "id");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        sourceObjectKey = Objects.requireNonNull(sourceObjectKey, "sourceObjectKey");
        mediaType = Objects.requireNonNull(mediaType, "mediaType");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        placeholders = List.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
        questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
        // Older persisted document JSON predates the asset collection. Treat a
        // missing field as empty so migrations remain backward compatible.
        assets = assets == null ? List.of() : List.copyOf(assets);
        if (assets.stream().map(DocumentAsset::objectKey).distinct().count() != assets.size()) {
            throw new IllegalArgumentException("document assets must have unique object keys");
        }
    }

    /** Backward-compatible constructor for callers that do not contain binary assets. */
    public Document(String id,
                    TenantId tenantId,
                    String sourceObjectKey,
                    String mediaType,
                    List<DocumentBlock> blocks,
                    List<Placeholder> placeholders,
                    List<Question> questions) {
        this(id, tenantId, sourceObjectKey, mediaType, blocks, placeholders, questions, List.of());
    }
}
