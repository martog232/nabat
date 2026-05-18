package org.example.nabat.application.port.in;

public interface ForgotPasswordUseCase {
    /** Sends a password-reset email if the address belongs to a known account. */
    void sendPasswordReset(String email);
}

