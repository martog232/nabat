package org.example.nabat.callsofia.adapter.out.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.nabat.callsofia.application.port.out.CallSofiaMap;
import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.shared.domain.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Reads Call Sofia's map the way its own page does. The anti-corruption layer for the live
 * data: the only class that knows the site's URLs, its form fields and its JSON.
 *
 * <p>Two requests per reading, and no more. {@code GET /signals} for the session cookie
 * and the CSRF token Rails puts in the page; then {@code POST /signals/map} with the
 * page's own filter set to 30 days, which answers with every report on the map in one
 * response — no paging, no bounds. That is the same traffic as one person opening the
 * page and choosing „Последните 30 дни“. It is gzipped on request, which takes the
 * answer from about 5.5 MB to a fraction of that; the JDK client does not ask for it on
 * its own, so this does.
 *
 * <p>Plain {@link HttpClient} rather than {@code RestClient}, because the exchange needs a
 * cookie carried from the first request to the second, and that is one line here.
 *
 * <p>Strict about the answer. A marker that does not parse fails the whole reading
 * instead of being skipped: the caller treats a report missing from the list as gone from
 * the map, and a skipped one would be recorded as answered by the municipality.
 */
@Component
public class CallSofiaMapClient implements CallSofiaMap {

    private static final Pattern CSRF_TOKEN = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]+)\"");

    /** The only status the map has shown: „В обработка“, its {@code public_signal_status_id} 2. */
    private static final int PROCESSING = 2;

    private final ObjectMapper json;
    private final URI baseUrl;
    private final String userAgent;
    private final Duration timeout;

    public CallSofiaMapClient(
        ObjectMapper json,
        @Value("${nabat.callsofia.live.base-url}") String baseUrl,
        @Value("${nabat.callsofia.live.user-agent}") String userAgent,
        @Value("${nabat.callsofia.live.timeout:PT60S}") Duration timeout
    ) {
        this.json = json;
        this.baseUrl = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    @Override
    public List<CallSofiaOpenSignal> openSignals() {
        HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        String token = csrfToken(send(http, request("signals").GET().build()));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("authenticity_token", token);
        form.put("client_signals_grid[period]", "30");
        form.put("client_signals_grid[page]", "1");
        HttpRequest mapRequest = request("signals/map")
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-CSRF-Token", token)
            .POST(HttpRequest.BodyPublishers.ofString(urlEncoded(form)))
            .build();

        return parse(send(http, mapRequest));
    }

    List<CallSofiaOpenSignal> parse(String body) {
        MapResponse response;
        try {
            response = json.readValue(body, MapResponse.class);
        } catch (JacksonException e) {
            throw new MapFormatChangedException("The map's answer is not the JSON expected", e);
        }
        if (!"markers".equals(response.type()) || response.markers() == null) {
            throw new MapFormatChangedException("The map's answer has no markers (type " + response.type() + ")");
        }

        List<CallSofiaOpenSignal> signals = new ArrayList<>(response.markers().size());
        for (Marker marker : response.markers()) {
            signals.add(toSignal(marker));
        }
        return signals;
    }

    private static CallSofiaOpenSignal toSignal(Marker m) {
        if (m.id() == null || m.categoryId() == null || m.categoryName() == null
            || m.title() == null || m.createdAt() == null) {
            throw new MapFormatChangedException("A marker lacks a field every marker has had: " + m);
        }
        return new CallSofiaOpenSignal(
            m.id(),
            m.registryNumber(),
            m.categoryId(),
            m.categoryName(),
            m.statusId() != null && m.statusId() == PROCESSING ? CallSofiaStatus.PROCESSING : CallSofiaStatus.UNKNOWN,
            m.title(),
            Instant.ofEpochSecond(m.createdAt()),
            location(m.lat(), m.lng()));
    }

    /** A point the map cannot place is a report without a point, not a broken reading. */
    private static Location location(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return null;
        }
        try {
            return Location.of(lat, lng);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String csrfToken(String page) {
        Matcher m = CSRF_TOKEN.matcher(page);
        if (!m.find()) {
            throw new MapFormatChangedException("The signals page carries no CSRF token");
        }
        return m.group(1);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUrl.resolve(path))
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .header("Accept-Encoding", "gzip");
    }

    private static String send(HttpClient http, HttpRequest request) {
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Call Sofia did not answer " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading " + request.uri(), e);
        }
        try (InputStream body = decoded(response)) {
            String text = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Call Sofia answered " + response.statusCode() + " to " + request.uri());
            }
            return text;
        } catch (IOException e) {
            throw new UncheckedIOException("Reading Call Sofia's answer to " + request.uri() + " failed", e);
        }
    }

    private static InputStream decoded(HttpResponse<InputStream> response) throws IOException {
        boolean gzipped = response.headers().firstValue("Content-Encoding")
            .map(encoding -> encoding.equalsIgnoreCase("gzip"))
            .orElse(false);
        return gzipped ? new GZIPInputStream(response.body()) : response.body();
    }

    private static String urlEncoded(Map<String, String> form) {
        return form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    /** The parts of the answer that are read. It also carries map icons and rendered HTML. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapResponse(String type, List<Marker> markers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Marker(
        Long id,
        @JsonProperty("acstre_number") String registryNumber,
        Double lat,
        Double lng,
        @JsonProperty("signal_category_id") Integer categoryId,
        @JsonProperty("category_name") String categoryName,
        @JsonProperty("public_signal_status_id") Integer statusId,
        String title,
        @JsonProperty("created_at_ts") Long createdAt
    ) {
    }
}
