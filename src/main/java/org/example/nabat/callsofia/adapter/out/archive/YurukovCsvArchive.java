package org.example.nabat.callsofia.adapter.out.archive;

import org.example.nabat.callsofia.application.port.out.CallSofiaArchive;
import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;
import org.example.nabat.shared.domain.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Boyan Yurukov's dump of Call Sofia, at {@code nabat.callsofia.archive-url}.
 *
 * <p>The anti-corruption layer for this module: the only class that knows the archive's
 * file names, its line layout, its district numbers and its status codes. Data published
 * under CC BY-SA 2.5 BG — https://opendata.yurukov.net/callsofia/data/.
 *
 * <p>The files look like CSV and are not, which is why there is no CSV parser here. The
 * title is the last field and is written raw — unquoted, with its own commas and quotes —
 * and the coordinates are one quoted field under a header that names two columns. A CSV
 * parser splits most titles into pieces and misreads the rest. What the files are is one
 * record per line, every field before the title strictly shaped, so each line is matched
 * whole and the title is whatever is left.
 *
 * <p>Times in the archive are Sofia's wall clock, with no offset.
 */
@Component
public class YurukovCsvArchive implements CallSofiaArchive {

    private static final Logger log = LoggerFactory.getLogger(YurukovCsvArchive.class);

    static final String CATEGORIES_FILE = "call.sofia.dump.categories.csv";
    static final String SIGNALS_FILE = "call.sofia.dump.reports.csv";
    static final String STATUS_FILE = "call.sofia.dump.status.csv";

    static final String CATEGORIES_HEADER = "id,name";
    static final String SIGNALS_HEADER = "id,imageid,category,region,kvartal,submitted,lng,lat,title";
    static final String STATUS_HEADER = "id,statetime,state";

    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern CATEGORY_LINE = Pattern.compile("^(\\d+),\"?(.*?)\"?$");

    /** id, image id, category, region, neighbourhood, submitted, "lng,lat", title. */
    private static final Pattern SIGNAL_LINE = Pattern.compile(
        "^(\\d+),\\d*,(\\d+),(\\d+),\\d*,(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}),"
            + "\"(?:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?))?\",(.*)$");

    /**
     * id, time, status, and who changed it. The last is a name of municipal staff and is
     * matched only to be thrown away.
     */
    private static final Pattern STATUS_LINE = Pattern.compile(
        "^(\\d+),(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}),(\\d+)(?:,.*)?$");

    private static final int MALFORMED_LINES_LOGGED = 5;

    private final ResourceLoader resources;
    private final String baseUrl;

    public YurukovCsvArchive(
        ResourceLoader resources,
        @Value("${nabat.callsofia.archive-url}") String baseUrl
    ) {
        this.resources = resources;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public List<CallSofiaCategory> categories() {
        try (Stream<CallSofiaCategory> categories = read(CATEGORIES_FILE, CATEGORIES_HEADER, YurukovCsvArchive::parseCategory)) {
            return categories.toList();
        }
    }

    @Override
    public Stream<CallSofiaSignal> signals() {
        return read(SIGNALS_FILE, SIGNALS_HEADER, YurukovCsvArchive::parseSignal);
    }

    /**
     * Each step's position is counted here, from the order of the lines. The file keeps a
     * report's history together and in order, and the order is the only thing that
     * separates two steps recorded in the same second.
     */
    @Override
    public Stream<CallSofiaStatusChange> statusChanges() {
        Positions positions = new Positions();
        return read(STATUS_FILE, STATUS_HEADER, YurukovCsvArchive::parseStatusLine)
            .map(line -> new CallSofiaStatusChange(
                line.signalId(), positions.next(line.signalId()), line.changedAt(), line.status()));
    }

    private <T> Stream<T> read(String file, String expectedHeader, Function<String, Optional<T>> parse) {
        String location = baseUrl + file;
        BufferedReader reader;
        String header;
        try {
            reader = new BufferedReader(new InputStreamReader(
                resources.getResource(location).getInputStream(), StandardCharsets.UTF_8));
            header = reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + location, e);
        }
        if (!expectedHeader.equals(stripBom(header))) {
            closeQuietly(reader);
            throw new ArchiveFormatChangedException(
                "%s starts with \"%s\", expected \"%s\"".formatted(location, header, expectedHeader));
        }

        AtomicLong lineNumber = new AtomicLong(1);
        AtomicLong malformed = new AtomicLong();
        return reader.lines()
            .map(line -> {
                long number = lineNumber.incrementAndGet();
                Optional<T> parsed = parse.apply(line);
                if (parsed.isEmpty() && malformed.incrementAndGet() <= MALFORMED_LINES_LOGGED) {
                    log.warn("{}:{} does not parse and is skipped: {}", file, number, abbreviate(line));
                }
                return parsed;
            })
            .flatMap(Optional::stream)
            .onClose(() -> {
                closeQuietly(reader);
                if (malformed.get() > 0) {
                    log.warn("{}: {} line(s) skipped as malformed", file, malformed.get());
                }
            });
    }

    static Optional<CallSofiaCategory> parseCategory(String line) {
        Matcher m = CATEGORY_LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new CallSofiaCategory(Integer.parseInt(m.group(1)), m.group(2)));
    }

    static Optional<CallSofiaSignal> parseSignal(String line) {
        Matcher m = SIGNAL_LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            Location location = m.group(5) == null
                ? null
                : Location.of(Double.parseDouble(m.group(6)), Double.parseDouble(m.group(5)));
            return Optional.of(new CallSofiaSignal(
                Long.parseLong(m.group(1)),
                Integer.parseInt(m.group(2)),
                YurukovRegions.districtCode(Integer.parseInt(m.group(3))),
                sofiaTime(m.group(4)),
                location,
                m.group(7)));
        } catch (RuntimeException e) {
            // Out-of-range coordinates, an id too large for a long: the line is shaped
            // right and still not a report. Same treatment as a line that does not match.
            return Optional.empty();
        }
    }

    static Optional<StatusLine> parseStatusLine(String line) {
        Matcher m = STATUS_LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        return status(Integer.parseInt(m.group(3)))
            .map(status -> new StatusLine(Long.parseLong(m.group(1)), sofiaTime(m.group(2)), status));
    }

    /** The archive's status codes, from its call.sofia.dump.statustype.csv. */
    static Optional<CallSofiaStatus> status(int code) {
        return Optional.ofNullable(switch (code) {
            case 0 -> CallSofiaStatus.UNKNOWN;
            case 1 -> CallSofiaStatus.AWAITING_PROCESSING;
            case 2 -> CallSofiaStatus.PROCESSING;
            case 3 -> CallSofiaStatus.AWAITING_REPLY;
            case 4 -> CallSofiaStatus.ANSWERED;
            case 5 -> CallSofiaStatus.CLOSED;
            case 6 -> CallSofiaStatus.REJECTED;
            default -> null;
        });
    }

    private static Instant sofiaTime(String text) {
        return LocalDateTime.parse(text, TIMESTAMP).atZone(SOFIA).toInstant();
    }

    private static String stripBom(String header) {
        return header != null && header.startsWith("﻿") ? header.substring(1) : header;
    }

    private static String abbreviate(String line) {
        return line.length() <= 200 ? line : line.substring(0, 200) + "…";
    }

    private static void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException e) {
            log.debug("Closing the archive reader failed", e);
        }
    }

    record StatusLine(long signalId, Instant changedAt, CallSofiaStatus status) {
    }

    /** Counts from 0 for each report, restarting whenever the report id changes. */
    private static final class Positions {
        private Long current;
        private int next;

        int next(long signalId) {
            if (!Objects.equals(current, signalId)) {
                current = signalId;
                next = 0;
            }
            return next++;
        }
    }
}
