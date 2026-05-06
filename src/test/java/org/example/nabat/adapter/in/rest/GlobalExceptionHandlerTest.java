package org.example.nabat.adapter.in.rest;

import org.example.nabat.domain.exception.AlertNotFoundException;
import org.example.nabat.domain.model.AlertId;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badCredentialsReturns401WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleBadCredentials(new BadCredentialsException("internal: pwd hash mismatch for user 42"));

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(GlobalExceptionHandler.MSG_BAD_CREDENTIALS, resp.getBody().message());
        // ensure the raw, sensitive message is not echoed
        assertFalse(resp.getBody().message().contains("pwd hash"));
    }

    @Test
    void illegalArgumentReturns400WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleIllegalArgument(new IllegalArgumentException("internal SQL: column foo not found"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(GlobalExceptionHandler.MSG_INVALID_REQUEST, resp.getBody().message());
        assertFalse(resp.getBody().message().contains("SQL"));
    }

    @Test
    void illegalStateReturns409WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleIllegalState(new IllegalStateException("duplicate key uk_alert_votes_alert_user"));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(GlobalExceptionHandler.MSG_CONFLICT, resp.getBody().message());
    }

    @Test
    void alertNotFoundReturns404WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleAlertNotFound(new AlertNotFoundException(AlertId.of(java.util.UUID.randomUUID())));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(GlobalExceptionHandler.MSG_NOT_FOUND, resp.getBody().message());
    }

    @Test
    void accessDeniedReturns403WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleAccessDenied(new AccessDeniedException("internal role mismatch: ADMIN required"));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(GlobalExceptionHandler.MSG_FORBIDDEN, resp.getBody().message());
        assertFalse(resp.getBody().message().contains("ADMIN required"));
    }

    @Test
    void validationReturns400EnvelopeWithFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must be a well-formed email address"));
        bindingResult.addError(new FieldError("request", "password", "must not be blank"));

        ResponseEntity<GlobalExceptionHandler.ValidationErrorResponse> resp =
            handler.handleValidationExceptions(new MethodArgumentNotValidException(methodParameter(), bindingResult));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getBody().status());
        assertEquals(GlobalExceptionHandler.MSG_VALIDATION, resp.getBody().message());
        assertEquals("must be a well-formed email address", resp.getBody().errors().get("email"));
        assertEquals("must not be blank", resp.getBody().errors().get("password"));
        assertNotNull(resp.getBody().timestamp());
    }

    private MethodParameter methodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyValidatedMethod", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyValidatedMethod(String ignored) {
    }
}



