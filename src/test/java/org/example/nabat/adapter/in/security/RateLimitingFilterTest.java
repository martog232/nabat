package org.example.nabat.adapter.in.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingFilterTest {

    // Very small capacity so we can exhaust the bucket quickly in tests
    private static final int CAPACITY = 3;
    private static final int WINDOW_MINUTES = 15;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(CAPACITY, WINDOW_MINUTES);
    }

    // ── shouldNotFilter ──────────────────────────────────────────────────────

    @Test
    void nonAuthPathsArePassedThrough() throws Exception {
        MockHttpServletRequest req = post("/api/v1/alerts/nearby", "1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // shouldNotFilter returns true → doFilterInternal is not called
        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    void getMethodOnAuthIsPassedThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        assertTrue(filter.shouldNotFilter(req));
    }

    // ── rate limiting ────────────────────────────────────────────────────────

    @Test
    void requestsBelowLimitAreAllowed() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletResponse res = doPostLogin("10.0.0.1");
            assertNotEquals(429, res.getStatus(),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void requestOverLimitReturns429() throws Exception {
        // Exhaust the bucket
        for (int i = 0; i < CAPACITY; i++) {
            doPostLogin("10.0.0.2");
        }
        MockHttpServletResponse over = doPostLogin("10.0.0.2");
        assertEquals(429, over.getStatus());
        assertNotNull(over.getHeader("X-RateLimit-Retry-After-Seconds"));
        assertTrue(over.getContentAsString().contains("Too many requests"));
    }

    @Test
    void differentIpsHaveSeparateBuckets() throws Exception {
        // Exhaust IP A
        for (int i = 0; i < CAPACITY; i++) {
            doPostLogin("20.0.0.1");
        }
        // IP B should still be unaffected
        MockHttpServletResponse ipB = doPostLogin("20.0.0.2");
        assertNotEquals(429, ipB.getStatus());
    }

    @Test
    void xForwardedForHeaderIsRespected() throws Exception {
        // Exhaust via forwarded IP header
        for (int i = 0; i < CAPACITY; i++) {
            doPostLoginForwarded("30.0.0.1");
        }
        MockHttpServletResponse over = doPostLoginForwarded("30.0.0.1");
        assertEquals(429, over.getStatus());
    }

    @Test
    void remainingTokensHeaderDecreases() throws Exception {
        filter.resetBuckets();
        MockHttpServletResponse first = doPostLogin("40.0.0.1");
        int remaining = Integer.parseInt(first.getHeader("X-RateLimit-Remaining"));
        assertEquals(CAPACITY - 1, remaining);
    }

    @Test
    void registerEndpointIsAlsoRateLimited() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            doPost("/api/v1/auth/register", "50.0.0.1");
        }
        MockHttpServletResponse over = doPost("/api/v1/auth/register", "50.0.0.1");
        assertEquals(429, over.getStatus());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MockHttpServletResponse doPostLogin(String ip) throws Exception {
        return doPost(LOGIN_PATH, ip);
    }

    private MockHttpServletResponse doPostLoginForwarded(String forwardedIp) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", LOGIN_PATH);
        req.addHeader("X-Forwarded-For", forwardedIp);
        return execute(req);
    }

    private MockHttpServletResponse doPost(String path, String ip) throws Exception {
        return execute(post(path, ip));
    }

    private MockHttpServletResponse execute(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(req, res, chain);
        // If the chain wasn't called, the filter short-circuited (rate limited)
        if (chain.getResponse() == null && res.getStatus() == 200) {
            // chain was invoked but no controller set status → mark as passed
        }
        return res;
    }

    private static MockHttpServletRequest post(String path, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        return req;
    }

    private static final String LOGIN_PATH = "/api/v1/auth/login";
}

