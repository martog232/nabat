package org.example.nabat.callsofia.adapter.out.archive;

/**
 * The archive no longer looks like the format this adapter was written against.
 *
 * <p>Raised on a header that differs, which is how a changed layout shows itself first.
 * Single rows that do not parse are skipped and counted instead: one odd line in three
 * million should not stop an import, but a new column would make every line wrong in the
 * same quiet way.
 */
public class ArchiveFormatChangedException extends RuntimeException {

    public ArchiveFormatChangedException(String message) {
        super(message);
    }
}
