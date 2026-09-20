package com.agentops.shared.exception;

import com.agentops.shared.api.ApiResponse;
import com.agentops.shared.api.FieldValidationError;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<List<FieldValidationError>>> handleBindingException(Exception exception) {
        var bindingResult = exception instanceof MethodArgumentNotValidException methodException
                ? methodException.getBindingResult()
                : ((BindException) exception).getBindingResult();

        List<FieldValidationError> errors = bindingResult.getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(new ApiResponse<>(
                false,
                ErrorCode.INVALID_REQUEST.code(),
                ErrorCode.INVALID_REQUEST.defaultMessage(),
                errors,
                com.agentops.shared.api.TraceIds.current(),
                java.time.Instant.now()
        ));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                ErrorCode.INVALID_REQUEST.code(),
                ErrorCode.INVALID_REQUEST.defaultMessage()
        ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.httpStatus())
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.failure(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.defaultMessage()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception exception) {
        return ResponseEntity.status(ErrorCode.TICKET_CONCURRENT_MODIFICATION.httpStatus())
                .body(ApiResponse.failure(
                        ErrorCode.TICKET_CONCURRENT_MODIFICATION.code(),
                        ErrorCode.TICKET_CONCURRENT_MODIFICATION.defaultMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request exception", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }
}
