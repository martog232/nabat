package org.example.nabat.adapter.in.rest;

import org.example.nabat.domain.exception.AlertNotFoundException;
import org.example.nabat.domain.model.AlertId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(false, resp.getBody().message().contains("pwd hash"));
    }

    @Test
    void illegalArgumentReturns400WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleIllegalArgument(new IllegalArgumentException("internal SQL: column foo not found"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(GlobalExceptionHandler.MSG_INVALID_REQUEST, resp.getBody().message());
        assertEquals(false, resp.getBody().message().contains("SQL"));
    }

    @Test
    void illegalStateReturns409WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleIllegalState(new IllegalStateException("duplicate key uk_alert_votes_alert_user"));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(GlobalExceptionHandler.MSG_CONFLICT, resp.getBody().message());
    }

    @Test
    void alertNotFoundReturns404WithCuratedMessage() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> resp =
            handler.handleAlertNotFound(new AlertNotFoundException(AlertId.of(java.util.UUID.randomUUID())));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals(GlobalExceptionHandler.MSG_NOT_FOUND, resp.getBody().message());
    }
}



