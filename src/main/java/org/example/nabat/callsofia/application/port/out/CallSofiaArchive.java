package org.example.nabat.callsofia.application.port.out;

import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;

import java.util.List;
import java.util.stream.Stream;

/**
 * Where Call Sofia's past reports are read from.
 *
 * <p>Call Sofia publishes none of this itself. Whatever implements this port is reading
 * someone else's unofficial copy, in a format nobody promised to keep, and its job is to
 * keep that format out of the rest of the module: what comes out of here is already in
 * nabat's terms.
 *
 * <p>The two streams hold a connection or a file open and must be closed.
 */
public interface CallSofiaArchive {

    List<CallSofiaCategory> categories();

    Stream<CallSofiaSignal> signals();

    /** Every report's history, each report's steps together and in order. */
    Stream<CallSofiaStatusChange> statusChanges();
}
