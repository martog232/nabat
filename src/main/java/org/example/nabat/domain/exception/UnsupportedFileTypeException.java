package org.example.nabat.domain.exception;

/**
 * The uploaded file is not an accepted image.
 *
 * <p>Maps to {@code 415 Unsupported Media Type}.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
