package com.reportai.auth.exception;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;

/** Public-safe exception mapped to HTTP 401 by the application exception handler. */
public final class AuthenticationRequiredException extends BusinessException {
    public AuthenticationRequiredException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
