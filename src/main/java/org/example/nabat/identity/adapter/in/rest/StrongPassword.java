package org.example.nabat.identity.adapter.in.rest;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;

/**
 * Applies {@link org.example.nabat.identity.domain.PasswordPolicy} to a request field.
 *
 * <p>Registration and password reset previously each carried their own
 * {@code @Size(min = 6)}, so the policy existed twice and could be tightened in one place
 * only. This annotation is the single way in.
 *
 * <p>The message is filled in by the validator rather than fixed here, because "must be at
 * least 10 characters" and "must contain a digit" send the user to different fixes, and a
 * combined message that lists every rule makes them work out which one they broke.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password does not meet the policy";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
