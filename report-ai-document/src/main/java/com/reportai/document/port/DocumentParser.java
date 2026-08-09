package com.reportai.document.port;

import com.reportai.document.model.Document;
import com.reportai.common.tenant.TenantId;

import java.io.InputStream;

public interface DocumentParser {
    boolean supports(String mediaType, String fileName);

    Document parse(InputStream input, String fileName, String sourceObjectKey, TenantId tenantId, String mediaType);
}
