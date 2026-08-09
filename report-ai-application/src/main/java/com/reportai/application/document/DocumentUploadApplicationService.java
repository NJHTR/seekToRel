package com.reportai.application.document;

import com.reportai.api.document.UploadDocumentRequest;
import com.reportai.api.document.UploadDocumentResponse;
import com.reportai.auth.model.Permission;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.id.IdGenerator;
import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.idempotency.IdempotencyRecord;
import com.reportai.common.idempotency.IdempotencyStore;
import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.Document;
import com.reportai.document.security.UploadPolicy;
import com.reportai.parser.DocumentParserPort;
import com.reportai.parser.ParserRequest;
import com.reportai.parser.ParserResult;
import com.reportai.parser.asset.ParsedAsset;
import com.reportai.storage.DocumentRepository;
import com.reportai.storage.ObjectStorage;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.storage.StoredObject;
import com.reportai.application.config.properties.ReportAiUploadProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Orchestrates a streamed, tenant-bound and idempotent document upload. */
@Service
public final class DocumentUploadApplicationService {
    private static final String OPERATION = "document-upload";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final AuthorizationPort authorizationPort;
    private final IdempotencyStore idempotencyStore;
    private final DocumentRepository documentRepository;
    private final ObjectIntegrityRepository objectIntegrityRepository;
    private final ObjectProvider<ObjectStorage> objectStorageProvider;
    private final List<DocumentParserPort> parsers;
    private final IdGenerator idGenerator;
    private final UploadPolicy uploadPolicy;
    private final int maxFileNameLength;

    public DocumentUploadApplicationService(AuthorizationPort authorizationPort,
                                            IdempotencyStore idempotencyStore,
                                            DocumentRepository documentRepository,
                                            ObjectIntegrityRepository objectIntegrityRepository,
                                            ObjectProvider<ObjectStorage> objectStorageProvider,
                                            List<DocumentParserPort> parsers,
                                            IdGenerator idGenerator,
                                            ReportAiUploadProperties uploadProperties) {
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.documentRepository = Objects.requireNonNull(documentRepository, "documentRepository");
        this.objectIntegrityRepository = Objects.requireNonNull(objectIntegrityRepository, "objectIntegrityRepository");
        this.objectStorageProvider = Objects.requireNonNull(objectStorageProvider, "objectStorageProvider");
        this.parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers"));
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        Objects.requireNonNull(uploadProperties, "uploadProperties");
        this.uploadPolicy = new UploadPolicy(uploadProperties.getMaxBytes(),
                uploadProperties.getAllowedMediaTypes(), uploadProperties.getAllowedExtensions());
        if (uploadProperties.getMaxFileNameLength() <= 0 || uploadProperties.getMaxFileNameLength() > 255) {
            throw new IllegalArgumentException("maxFileNameLength must be between 1 and 255");
        }
        this.maxFileNameLength = uploadProperties.getMaxFileNameLength();
    }

    public UploadDocumentResponse upload(MultipartFile file, String rawIdempotencyKey) {
        var principal = authorizationPort.requireAuthenticated();
        authorizationPort.requirePermission(Permission.DOCUMENT_WRITE);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "document file must not be empty");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        String mediaType = normalizeMediaType(file.getContentType());
        long contentLength = file.getSize();
        byte[] signature = readSignature(file);
        uploadPolicy.validate(fileName, mediaType, contentLength, signature);
        UploadDocumentRequest metadata = new UploadDocumentRequest(fileName, mediaType, contentLength);
        String requestHash = requestHash(metadata, file);
        IdempotencyKey idempotencyKey = parseIdempotencyKey(rawIdempotencyKey);
        TenantId tenantId = principal.tenantId();

        String documentId = idGenerator.nextId();
        IdempotencyRecord reservation = new IdempotencyRecord(tenantId, OPERATION, idempotencyKey,
                requestHash, documentId, IdempotencyRecord.Status.RESERVED, Instant.now().plus(IDEMPOTENCY_TTL));
        if (!idempotencyStore.reserve(reservation)) {
            return existingResponse(tenantId, idempotencyKey, requestHash, mediaType, contentLength);
        }

        String sourceObjectKey = objectKey(tenantId, documentId);
        List<String> uploadedObjectKeys = new ArrayList<>();
        try {
            DocumentParserPort parser = parsers.stream()
                    .filter(candidate -> candidate.supports(mediaType, fileName))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT,
                            "unsupported document format"));
            ParserResult parsed;
            try (InputStream input = file.getInputStream()) {
                parsed = parser.parse(new ParserRequest(input, fileName, sourceObjectKey, tenantId,
                        mediaType, contentLength, documentId));
            }
            ObjectStorage objectStorage = objectStorageProvider.getIfAvailable();
            if (objectStorage == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "object storage is not configured");
            }
            StoredObject sourceReference = new StoredObject(sourceObjectKey, mediaType, contentLength,
                    sha256(file));
            try (InputStream input = file.getInputStream()) {
                objectStorage.put(sourceReference, input);
            }
            uploadedObjectKeys.add(sourceObjectKey);
            for (ParsedAsset asset : parsed.assets()) {
                StoredObject assetReference = new StoredObject(asset.metadata().objectKey(),
                        asset.metadata().mediaType(), asset.metadata().size(), asset.metadata().sha256());
                try (InputStream input = new ByteArrayInputStream(asset.content())) {
                    objectStorage.put(assetReference, input);
                }
                uploadedObjectKeys.add(asset.metadata().objectKey());
            }
            documentRepository.save(parsed.document());
            Instant integrityCreatedAt = Instant.now();
            objectIntegrityRepository.save(new ObjectIntegrity(idGenerator.nextId(), tenantId, "DOCUMENT", documentId,
                    "SOURCE", sourceReference, integrityCreatedAt));
            int assetOrdinal = 0;
            for (ParsedAsset asset : parsed.assets()) {
                StoredObject assetReference = new StoredObject(asset.metadata().objectKey(),
                        asset.metadata().mediaType(), asset.metadata().size(), asset.metadata().sha256());
                objectIntegrityRepository.save(new ObjectIntegrity(idGenerator.nextId(), tenantId, "DOCUMENT",
                        documentId, "ASSET_" + (++assetOrdinal), assetReference,
                        integrityCreatedAt));
            }
            idempotencyStore.complete(tenantId, OPERATION, idempotencyKey);
            return response(parsed, "PARSED");
        } catch (RuntimeException | IOException failure) {
            if (!uploadedObjectKeys.isEmpty()) {
                try {
                    ObjectStorage objectStorage = objectStorageProvider.getIfAvailable();
                    if (objectStorage != null) {
                        for (String uploadedObjectKey : uploadedObjectKeys) {
                            objectStorage.delete(uploadedObjectKey);
                        }
                    }
                } catch (RuntimeException cleanupFailure) {
                    // Preserve the original failure; a scheduled orphan reconciler handles cleanup retries.
                }
            }
            try {
                objectIntegrityRepository.deleteByOwner(tenantId, "DOCUMENT", documentId);
            } catch (RuntimeException cleanupFailure) {
                // Preserve the original failure; a reconciliation job handles stale integrity rows.
            }
            idempotencyStore.release(tenantId, OPERATION, idempotencyKey);
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "document could not be read");
        }
    }

    private UploadDocumentResponse existingResponse(TenantId tenantId, IdempotencyKey key,
                                                     String requestHash, String mediaType,
                                                     long contentLength) {
        IdempotencyRecord existing = idempotencyStore.find(tenantId, OPERATION, key)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "request is already being processed"));
        if (!existing.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "idempotency key was used with different document content");
        }
        String status = existing.status() == IdempotencyRecord.Status.COMPLETED ? "PARSED" : "PROCESSING";
        return new UploadDocumentResponse(existing.resourceId(), status, mediaType, contentLength, List.of());
    }

    private static UploadDocumentResponse response(ParserResult parsed, String status) {
        return new UploadDocumentResponse(parsed.document().id(), status, parsed.document().mediaType(),
                parsed.inputBytes(), parsed.warnings().stream().map(warning -> warning.code() + ": " + warning.message()).toList());
    }

    private static byte[] readSignature(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(4);
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "document could not be read");
        }
    }

    private static String requestHash(UploadDocumentRequest metadata, MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((metadata.fileName() + "\n" + metadata.mediaType() + "\n"
                    + metadata.contentLength() + "\n").getBytes(StandardCharsets.UTF_8));
            try (InputStream input = file.getInputStream()) {
                input.transferTo(new java.security.DigestOutputStream(OutputStreamDiscard.INSTANCE, digest));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException failure) {
            throw new IllegalStateException("request fingerprint could not be calculated", failure);
        }
    }

    private static IdempotencyKey parseIdempotencyKey(String raw) {
        try {
            return new IdempotencyKey(raw);
        } catch (RuntimeException failure) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "Idempotency-Key must contain 8-128 safe characters");
        }
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank() || original.indexOf('\0') >= 0
                || original.indexOf('/') >= 0 || original.indexOf('\\') >= 0
                || original.contains("..") || original.chars().anyMatch(Character::isISOControl)
                || original.strip().length() > maxFileNameLength) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid document file name");
        }
        return original.strip();
    }

    private static String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "document media type is required");
        }
        return mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String objectKey(TenantId tenantId, String documentId) {
        String tenantHash = HexFormat.of().formatHex(sha256(tenantId.value())).substring(0, 32);
        return "documents/" + tenantHash + "/" + documentId + ".docx";
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException failure) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "document could not be read");
        }
    }

    /** A zero-allocation sink used by DigestOutputStream while reading the multipart stream. */
    private static final class OutputStreamDiscard extends java.io.OutputStream {
        private static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();

        private OutputStreamDiscard() {
        }

        @Override
        public void write(int value) {
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
        }
    }
}
