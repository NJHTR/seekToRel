package com.reportai.application.web;

import com.reportai.common.api.ErrorCode;
import com.reportai.common.api.Result;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.trace.TraceContext;
import com.reportai.parser.DocumentParseException;
import com.reportai.application.progress.ProgressEventHub;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(Result.failure(exception.getErrorCode(), TraceContext.getOrCreate()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception exception) {
        log.debug("request validation failed", exception);
        return ResponseEntity.badRequest()
                .body(Result.failure(ErrorCode.INVALID_ARGUMENT, TraceContext.getOrCreate()));
    }

    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<Result<Void>> handleDocumentParse(DocumentParseException exception) {
        log.info("document rejected by parser, traceId={}: {}", TraceContext.getOrCreate(), exception.getMessage());
        return ResponseEntity.badRequest()
                .body(Result.failure(ErrorCode.INVALID_ARGUMENT, TraceContext.getOrCreate()));
    }

    @ExceptionHandler(ProgressEventHub.ProgressSubscriptionLimitException.class)
    public ResponseEntity<Result<Void>> handleProgressCapacity(
            ProgressEventHub.ProgressSubscriptionLimitException exception) {
        log.info("progress stream capacity reached traceId={}", TraceContext.getOrCreate());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "5")
                .body(Result.failure(ErrorCode.RATE_LIMITED, TraceContext.getOrCreate()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception) {
        String traceId = TraceContext.getOrCreate();
        log.error("unhandled request failure, traceId={}", traceId, exception);
        return ResponseEntity.internalServerError()
                .body(Result.failure(ErrorCode.INTERNAL_ERROR, traceId));
    }
}
