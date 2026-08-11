package org.example.nabat.identity.adapter.in.rest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.example.nabat.identity.domain.PasswordPolicy;

/**
 * Turns a {@link PasswordPolicy.Violation} into the message the user sees.
 *
 * <p>The policy decides; this only translates. Keeping the rules out of here is what lets
 * registration, password reset and any future change-password path share one definition.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Absence is @NotBlank's job. Reporting it here too would show the user two errors
        // for one empty field.
        if (password == null) {
            return true;
        }

        return PasswordPolicy.check(password)
            .map(violation -> reject(violation, context))
            .orElse(true);
    }

    private boolean reject(PasswordPolicy.Violation violation, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(messageFor(violation)).addConstraintViolation();
        return false;
    }

    private String messageFor(PasswordPolicy.Violation violation) {
        return switch (violation) {
            case TOO_SHORT ->
                "Password must be at least " + PasswordPolicy.MIN_LENGTH + " characters";
            case NO_LETTER -> "Password must contain at least one letter";
            case NO_DIGIT -> "Password must contain at least one digit";
            // Deliberately explains itself. "Too long" on a password reads as an arbitrary
            // limit, and a user who picked a long passphrase did the right thing and
            // deserves to know why it is being refused.
            case TOO_LONG ->
                "Password must be at most " + PasswordPolicy.MAX_BYTES
                + " bytes (about " + PasswordPolicy.MAX_BYTES + " characters, fewer for "
                + "non-Latin alphabets). Longer passwords are silently truncated by the "
                + "password hash, so the extra length would not protect the account";
        };
    }
}
