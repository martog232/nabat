package org.example.nabat.callsofia.adapter.out.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client against a stand-in for call.sofia.bg that behaves the way the real one did on
 * 2026-10-13: a session cookie and a CSRF token from the page, the map refused without
 * them, and gzip only for a client that asks. Never the real site.
 */
class CallSofiaMapClientTest {

    private static final String TOKEN = "tok3n/with+chars==";
    private static final String SESSION = "_ng_admin_production_session=abc123";

    private HttpServer server;
    private final AtomicReference<String> mapBody = new AtomicReference<>(fixture());
    private final AtomicReference<Integer> mapStatus = new AtomicReference<>(200);
    private final AtomicReference<String> page =
        new AtomicReference<>("<html><head><meta name=\"csrf-token\" content=\"" + TOKEN + "\" /></head></html>");
    private final AtomicReference<String> seenForm = new AtomicReference<>();
    private final AtomicReference<String> seenUserAgent = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/signals", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/signals/map")) {
                answerMap(exchange);
            } else {
                exchange.getResponseHeaders().add("Set-Cookie", SESSION + "; path=/; HttpOnly");
                respond(exchange, 200, page.get());
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsEveryMarkerInNabatsTerms() {
        List<CallSofiaOpenSignal> signals = client().openSignals();

        assertThat(signals).extracting(CallSofiaOpenSignal::id).containsExactly(788076L, 802277L, 795000L);
        CallSofiaOpenSignal first = signals.getFirst();
        assertThat(first.registryNumber()).isEqualTo("Към СОА26-КЦ01-103731-[1]/13.09.2026");
        assertThat(first.categoryId()).isEqualTo(1013);
        assertThat(first.categoryName()).isEqualTo("Дървета и растителност");
        assertThat(first.status()).isEqualTo(CallSofiaStatus.PROCESSING);
        assertThat(first.submittedAt()).isEqualTo(Instant.ofEpochSecond(1789308210));
        assertThat(first.location()).isEqualTo(Location.of(42.70232423476643, 23.309729397296906));
        assertThat(signals.get(1).title()).isEqualTo("Дупка на \"Александър Малинов\", до спирката");
    }

    @Test
    void aMarkerWithNoPointHasNoLocationAndAStatusNotSeenBeforeIsUnknown() {
        CallSofiaOpenSignal unplaced = client().openSignals().get(2);

        assertThat(unplaced.location()).isNull();
        assertThat(unplaced.status()).isEqualTo(CallSofiaStatus.UNKNOWN);
    }

    /** What the real site checks, and what a browser sends: cookie, token, the 30-day filter. */
    @Test
    void asksLikeTheMapsOwnPage() {
        client().openSignals();

        assertThat(seenForm.get())
            .contains("authenticity_token=" + TOKEN)
            .contains("client_signals_grid[period]=30")
            .contains("client_signals_grid[page]=1");
        assertThat(seenUserAgent.get()).isEqualTo("nabat-tests");
    }

    /** Skipping it would record it as gone from the map — answered, by the municipality. */
    @Test
    void aMarkerThatDoesNotParseFailsTheWholeReading() {
        mapBody.set(fixture().replace("\"created_at_ts\":1791899273", "\"created\":1791899273"));

        assertThatThrownBy(() -> client().openSignals())
            .isInstanceOf(MapFormatChangedException.class)
            .hasMessageContaining("802277");
    }

    @Test
    void anAnswerWithoutMarkersIsAChangedFormat() {
        mapBody.set("{\"type\":\"list\",\"items\":[]}");

        assertThatThrownBy(() -> client().openSignals()).isInstanceOf(MapFormatChangedException.class);
    }

    @Test
    void aPageWithoutACsrfTokenIsAChangedFormat() {
        page.set("<html><head></head></html>");

        assertThatThrownBy(() -> client().openSignals())
            .isInstanceOf(MapFormatChangedException.class)
            .hasMessageContaining("CSRF");
    }

    @Test
    void anErrorFromTheSiteFailsTheReading() {
        mapStatus.set(503);

        assertThatThrownBy(() -> client().openSignals())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("503");
    }

    private CallSofiaMapClient client() {
        return new CallSofiaMapClient(
            JsonMapper.builder().build(),
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "nabat-tests",
            Duration.ofSeconds(5));
    }

    /** Refuses as Rails would: no session cookie or a wrong token is a 422. */
    private void answerMap(HttpExchange exchange) throws IOException {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        String token = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        seenForm.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        seenUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
        if (!"POST".equals(exchange.getRequestMethod()) || cookie == null || !cookie.contains(SESSION) || !TOKEN.equals(token)) {
            respond(exchange, 422, "{\"error\":\"InvalidAuthenticityToken\"}");
            return;
        }
        respond(exchange, mapStatus.get(), mapBody.get());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
                out.write(bytes);
            }
            bytes = gz.toByteArray();
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String fixture() {
        try (InputStream in = Objects.requireNonNull(
            CallSofiaMapClientTest.class.getResourceAsStream("/callsofia-live/map.json"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
