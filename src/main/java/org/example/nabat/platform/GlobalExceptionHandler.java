package org.example.nabat.platform;

import org.example.nabat.shared.exception.ExternalServiceUnavailableException;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.identity.domain.AuthenticationFailedException;
import org.example.nabat.identity.domain.EmailAlreadyRegisteredException;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.example.nabat.notification.domain.NotificationNotFoundException;
import org.example.nabat.media.domain.UnsupportedFileTypeException;
import org.example.nabat.identity.domain.UserNotFoundException;
import org.example.nabat.voting.domain.VoteConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps exceptions to HTTP responses.
 *
 * <p>Messages returned to clients are curated constants — internal detail stays in
 * the logs. Because that makes the prose useless for branching, every response also
 * carries a stable machine-readable {@code code}: the frontend should switch on
 * that, not on the message. Previously the frontend dug through {@code data.message}
 * looking for something meaningful and found only "Invalid request", because the
 * handler had replaced whatever the service threw.
 */
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
    static final String MSG_VOTE_CONFLICT   = "You have already cast this vote";
    static final String MSG_PAYLOAD_TOO_LARGE = "File is too large";
    static final String MSG_UNSUPPORTED_MEDIA = "Unsupported file type";
    static final String MSG_UNAVAILABLE     = "A required service is temporarily unavailable. Please try again.";
    static final String MSG_INTERNAL        = "Something went wrong. Please try again.";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDenied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", MSG_FORBIDDEN);
    }

    /** Domain-level authorization failure — see {@link NotAuthorizedException}. */
    @ExceptionHandler(NotAuthorizedException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthorized(NotAuthorizedException ex) {
        log.warn("NotAuthorized: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", MSG_FORBIDDEN);
    }

    /** Thrown by Spring Security itself; the application layer uses the domain type below. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("BadCredentials: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", MSG_BAD_CREDENTIALS);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex) {
        log.warn("AuthenticationFailed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", MSG_BAD_CREDENTIALS);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", MSG_INVALID_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalState: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", MSG_CONFLICT);
    }

    /**
     * A genuine vote conflict, distinguished from an integration failure so the
     * frontend can keep quietly resyncing on the former without also swallowing
     * the latter.
     */
    @ExceptionHandler(VoteConflictException.class)
    public ResponseEntity<ErrorResponse> handleVoteConflict(VoteConflictException ex) {
        log.debug("VoteConflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "VOTE_ALREADY_CAST", MSG_VOTE_CONFLICT);
    }

    /**
     * A downstream dependency is unhealthy or misconfigured. 503, not 409: this is
     * our fault, it is retryable, and it must be visible to the user and to
     * monitoring rather than looking like a duplicate-vote no-op.
     */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(ExternalServiceUnavailableException ex) {
        log.error("Downstream service unavailable: {}", ex.getMessage(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", MSG_UNAVAILABLE);
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(UnsupportedFileTypeException ex) {
        log.info("Rejected upload: {}", ex.getMessage());
        // Message is safe and useful — it names the accepted formats.
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", ex.getMessage());
    }

    /** Raised by Spring when the multipart limits in application.properties are exceeded. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        log.info("Rejected upload: too large");
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", MSG_PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleEmailTaken(EmailAlreadyRegisteredException ex) {
        log.info("Registration conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", ex.getMessage());
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAlertNotFound(AlertNotFoundException ex) {
        log.warn("AlertNotFound: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", MSG_NOT_FOUND);
    }

    @ExceptionHandler({UsernameNotFoundException.class, UserNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleUserNotFound(RuntimeException ex) {
        log.warn("UserNotFound: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", MSG_NOT_FOUND);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException ex) {
        log.warn("NotificationNotFound: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", MSG_NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
        MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            // Class-level (cross-field) constraints produce ObjectError, not FieldError.
            // The previous unconditional cast turned those into a 500.
            String fieldName = error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();
            errors.put(fieldName, error.getDefaultMessage());
        });

        ValidationErrorResponse error = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_FAILED",
            MSG_VALIDATION,
            errors,
            Instant.now(),
            MDC.get("traceId")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Constraint violations on request parameters, from {@code @Validated} on the
     * controller (as opposed to {@code @Valid} on a request body, which raises
     * {@link MethodArgumentNotValidException} and is handled above).
     *
     * <p>Needed for the bounds on {@code /alerts/nearby}: without it these would fall
     * through to the catch-all and a `radiusKm` of 500 would be reported as a server
     * error rather than a bad request.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = String.valueOf(violation.getPropertyPath());
            // Property paths look like "getNearbyAlerts.radiusKm" — keep the last segment.
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(field, violation.getMessage());
        });

        log.warn("Parameter validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(), "VALIDATION_FAILED", MSG_VALIDATION,
            errors, Instant.now(), MDC.get("traceId")));
    }

    /**
     * Spring MVC's own request-level failures, translated into our envelope while
     * keeping the status Spring already determined.
     *
     * <p>Covers wrong HTTP method (405), unsupported or unacceptable media type
     * (415/406), unreadable request bodies, missing parameters or parts, and unmatched
     * paths (404).
     *
     * <p>Without this, the {@code Exception} catch-all below would swallow all of them
     * and report 500 — so a client calling an endpoint with the wrong verb would be
     * told the server had broken. These types share no useful {@code Throwable}
     * supertype, so they are listed explicitly; the status is read back off
     * {@link org.springframework.web.ErrorResponse}, which they all implement.
     */
    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMediaTypeNotAcceptableException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleSpringMvcError(Exception ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus resolved = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (resolved != null) {
                status = resolved;
            }
        }
        log.warn("Request rejected by the framework: {} {}", status.value(), ex.getClass().getSimpleName());
        return build(status, "REQUEST_NOT_ACCEPTABLE", status.getReasonPhrase());
    }

    /**
     * Catch-all, so a genuinely unexpected exception returns this envelope — with a
     * traceId tying the response to the stack trace in the logs — instead of Spring's
     * default error body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", MSG_INTERNAL);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), code, message, Instant.now(), MDC.get("traceId")));
    }

    /**
     * @param code stable identifier for the failure kind — safe to branch on, unlike
     *             {@code message}, which is prose and may be reworded
     */
    public record ErrorResponse(
        int status,
        String code,
        String message,
        Instant timestamp,
        String traceId
    ) {}

    public record ValidationErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> errors,
        Instant timestamp,
        String traceId
    ) {}
}
