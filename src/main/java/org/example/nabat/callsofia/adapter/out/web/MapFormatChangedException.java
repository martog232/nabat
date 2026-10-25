package org.example.nabat.callsofia.adapter.out.web;

/**
 * Call Sofia's map answered, but not in the shape {@link CallSofiaMapClient} was written
 * against. The site was rebuilt once already, in July 2026, and that is what this looks like.
 */
public class MapFormatChangedException extends RuntimeException {

    public MapFormatChangedException(String message) {
        super(message);
    }

    public MapFormatChangedException(String message, Throwable cause) {
        super(message, cause);
    }
}
