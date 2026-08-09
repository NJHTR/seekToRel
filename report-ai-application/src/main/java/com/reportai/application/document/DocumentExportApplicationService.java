package com.reportai.application.document;

import com.reportai.auth.model.Permission;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import com.reportai.document.model.Document;
import com.reportai.storage.DocumentRepository;
import com.reportai.word.ReleaseResult;
import com.reportai.word.WordBuildOptions;
import com.reportai.word.WordReleaseService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public final class DocumentExportApplicationService {
    private static final int MAX_EXPORT_BYTES = 50 * 1024 * 1024;
    private final AuthorizationPort authorization;
    private final DocumentRepository documents;
    private final WordReleaseService releaseService;

    public DocumentExportApplicationService(AuthorizationPort authorization, DocumentRepository documents,
                                            WordReleaseService releaseService) {
        this.authorization = authorization;
        this.documents = documents;
        this.releaseService = releaseService;
    }

    public byte[] export(String documentId) {
        authorization.requireAuthenticated();
        authorization.requirePermission(Permission.EXPORT_READ);
        Document document = documents.findById(TenantContext.require(), validId(documentId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "document not found"));
        BoundedOutputStream output = new BoundedOutputStream(MAX_EXPORT_BYTES);
        ReleaseResult result = releaseService.release(document, output, WordBuildOptions.defaults());
        if (!result.released()) {
            throw new BusinessException(ErrorCode.CONFLICT, "document review did not pass");
        }
        return output.toByteArray();
    }

    private static String validId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "documentId is invalid");
        }
        return value;
    }

    private static final class BoundedOutputStream extends ByteArrayOutputStream {
        private final int limit;
        private BoundedOutputStream(int limit) { this.limit = limit; }
        @Override public synchronized void write(int value) {
            ensureCapacity(1);
            super.write(value);
        }
        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            ensureCapacity(length);
            super.write(bytes, offset, length);
        }
        private void ensureCapacity(int additional) {
            if (additional < 0 || size() > limit - additional) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "export exceeds the maximum size");
            }
        }
    }
}
