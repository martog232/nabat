package org.example.nabat.adapter.in.rest;

import org.example.nabat.domain.exception.AlertNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Curated, safe-to-return messages. Internal details stay in the logs. */
    static final String MSG_BAD_CREDENTIALS = "Invalid email or password";
    static final String MSG_INVALID_REQUEST = "Invalid request";
    static final String MSG_CONFLICT        = "Request conflicts with current state";
    static final String MSG_NOT_FOUND       = "Resource not found";
    static final String MSG_VALIDATION      = "Validation failed";
    static final String MSG_FORBIDDEN       = "Forbidden";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDenied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, MSG_FORBIDDEN);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("BadCredentials: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, MSG_BAD_CREDENTIALS);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, MSG_INVALID_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalState: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, MSG_CONFLICT);
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAlertNotFound(AlertNotFoundException ex) {
        log.warn("AlertNotFound: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, MSG_NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
        MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse error = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            MSG_VALIDATION,
            errors,
            Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, Instant.now()));
    }

    public record ErrorResponse(
        int status,
        String message,
        Instant timestamp
    ) {}

    public record ValidationErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        Instant timestamp
    ) {}
}
