package com.reportai.auth.exception;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;

/** Public-safe exception mapped to HTTP 403 by the application exception handler. */
public final class AccessDeniedException extends BusinessException {
    public AccessDeniedException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
