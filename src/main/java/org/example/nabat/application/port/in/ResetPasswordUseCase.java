package org.example.nabat.application.port.in;

public interface ResetPasswordUseCase {
    /**
     * Validates the reset token and replaces the user's password.
     *
     * @param token       the secret token received by email
     * @param newPassword the user's desired new password (already validated by the caller)
     */
    void resetPassword(String token, String newPassword);
}

