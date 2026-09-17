package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.http.RequestSigner;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacTransportTest {

    private static final String MERCHANT = "3f2a1b4c-5d6e-7f80-9a1b-2c3d4e5f6071";
    private static final String API_KEY = "test_api_key_123";
    private static final String PAYOUT_OK = "{\"uuid\":\"abc\",\"status\":\"paid\",\"network\":\"ETH_MAINNET\","
            + "\"coin\":\"ETH\",\"amount\":\"1\",\"to_address\":\"0x\"}";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private Options.Builder options(String basePath) {
        return Options.builder()
                .merchantId(MERCHANT)
                .apiKey(API_KEY)
                .baseUrl(server.url(basePath).toString())
                .maxRetries(2)
                .initialRetryDelay(Duration.ofMillis(1))
                .maxRetryDelay(Duration.ofMillis(5));
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Recomputes X-CC-Signature from what arrived on the wire. */
    private static void assertHmacValid(RecordedRequest r, String routePath, String query) {
        String ts = r.getHeader(RequestSigner.HEADER_TIMESTAMP);
        String nonce = r.getHeader(RequestSigner.HEADER_NONCE);
        assertNotNull(ts);
        assertTrue(nonce.matches("[0-9a-f]{32}"), nonce);
        String expected = RequestSigner.signHmacV1(API_KEY, ts, nonce, r.getMethod(), routePath, query,
                r.getHeader("Merchant"), r.getHeader(RequestSigner.HEADER_IDEMPOTENCY_KEY),
                r.getBody().clone().readByteArray());
        assertEquals("v1=" + expected, r.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE));
    }

    @Test
    void sendsHmacV1HeadersOnly() throws Exception {
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            client.payouts().info("abc");
        }
        RecordedRequest r = server.takeRequest();

        assertNull(r.getHeader("Signature"));
        assertTrue(Math.abs(Long.parseLong(r.getHeader(RequestSigner.HEADER_TIMESTAMP)) - now()) <= 5);
        assertNull(r.getHeader(RequestSigner.HEADER_IDEMPOTENCY_KEY));
        assertHmacValid(r, "/v1/payout/info", "");
    }

    /** A header added by a caller's interceptor arrives, but the signature was computed without it. */
    @Test
    void interceptorHeadersAreNotCoveredBySignature() throws Exception {
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        OkHttpClient http = new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header(RequestSigner.HEADER_IDEMPOTENCY_KEY, "payout-1")
                        .build()))
                .build();
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").httpClient(http).build())) {
            client.payouts().info("abc");
        } finally {
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }

        RecordedRequest r = server.takeRequest();
        assertEquals("payout-1", r.getHeader(RequestSigner.HEADER_IDEMPOTENCY_KEY));
        String ts = r.getHeader(RequestSigner.HEADER_TIMESTAMP);
        String nonce = r.getHeader(RequestSigner.HEADER_NONCE);
        byte[] body = r.getBody().readByteArray();
        assertEquals("v1=" + RequestSigner.signHmacV1(API_KEY, ts, nonce, r.getMethod(), "/v1/payout/info", "",
                        r.getHeader("Merchant"), "", body),
                r.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE));
        assertNotEquals("v1=" + RequestSigner.signHmacV1(API_KEY, ts, nonce, r.getMethod(), "/v1/payout/info", "",
                        r.getHeader("Merchant"), "payout-1", body),
                r.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE));
    }

    @Test
    void signsRoutePathWithoutBaseUrlPrefixAndActualQuery() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));
        HttpTransport transport = new HttpTransport(options("/gateway").build());
        try {
            transport.send("/v1/payments/history?a=1&b=2", Map.of(), Object.class);
        } finally {
            transport.http().dispatcher().executorService().shutdown();
        }

        RecordedRequest r = server.takeRequest();
        assertEquals("/gateway/v1/payments/history?a=1&b=2", r.getPath());
        assertHmacValid(r, "/v1/payments/history", "a=1&b=2");
    }

    /** The path is signed percent-decoded, the query exactly as the URL carries it. */
    @Test
    void signsPercentDecodedPathAndRawQuery() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));
        HttpTransport transport = new HttpTransport(options("/gateway").build());
        try {
            transport.request("GET", "/v1/orders/payout%2F8814%20x?ref=a%2Fb&q=%D1%82", null, Object.class);
        } finally {
            transport.http().dispatcher().executorService().shutdown();
        }

        RecordedRequest r = server.takeRequest();
        assertEquals("GET", r.getMethod());
        assertEquals("/gateway/v1/orders/payout%2F8814%20x?ref=a%2Fb&q=%D1%82", r.getPath());
        assertEquals(0, r.getBodySize());
        assertNull(r.getHeader("Content-Type"));
        assertHmacValid(r, "/v1/orders/payout/8814 x", "ref=a%2Fb&q=%D1%82");
    }

    /** A method in mixed case goes out and is signed with {@code a-z} upper-cased. */
    @Test
    void methodIsUpperCasedOnTheWireAndInTheSignature() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));
        HttpTransport transport = new HttpTransport(options("/").build());
        try {
            transport.request("dElEtE", "/v1/payout/info", Map.of(), Object.class);
        } finally {
            transport.http().dispatcher().executorService().shutdown();
        }

        RecordedRequest r = server.takeRequest();
        assertEquals("DELETE", r.getMethod());
        assertHmacValid(r, "/v1/payout/info", "");
    }

    /** The idempotency key is on the wire and inside the signature, on a service call and a raw one. */
    @Test
    void idempotencyKeyIsSignedOnEveryCallOfTheView() throws Exception {
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        server.enqueue(new MockResponse().setBody("{}"));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            CryptoChiefClient keyed = client.withIdempotencyKey("payout-2026-09-16-0001");
            keyed.payouts().info("abc");
            keyed.request("GET", "/v1/payout/info?uuid=abc", null, Object.class);
        }

        for (int i = 0; i < 2; i++) {
            RecordedRequest r = server.takeRequest();
            assertEquals("payout-2026-09-16-0001", r.getHeader(RequestSigner.HEADER_IDEMPOTENCY_KEY));
            assertHmacValid(r, "/v1/payout/info", i == 0 ? "" : "uuid=abc");
        }
    }

    @Test
    void recomputesHeadersOnEveryRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"SERVICE_ERROR\",\"msg\":\"try again\"}"));
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"SERVICE_ERROR\",\"msg\":\"try again\"}"));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            client.payouts().info("abc");
        }
        assertEquals(3, server.getRequestCount());
        RecordedRequest first = server.takeRequest();
        RecordedRequest second = server.takeRequest();
        RecordedRequest third = server.takeRequest();
        for (RecordedRequest r : new RecordedRequest[] {first, second, third}) {
            assertHmacValid(r, "/v1/payout/info", "");
            assertNull(r.getHeader("Signature"));
        }
        assertNotEquals(first.getHeader(RequestSigner.HEADER_NONCE), second.getHeader(RequestSigner.HEADER_NONCE));
        assertNotEquals(second.getHeader(RequestSigner.HEADER_NONCE), third.getHeader(RequestSigner.HEADER_NONCE));
        assertNotEquals(first.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE),
                second.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE));
    }

    private static MockResponse timestampOutOfRange(long serverTime) {
        return new MockResponse().setResponseCode(401).setBody("{\"ok\":false,"
                + "\"error\":\"SIGNATURE_TIMESTAMP_OUT_OF_RANGE\","
                + "\"msg\":\"X-CC-Timestamp differs from server time by more than 300 seconds\","
                + "\"server_time\":" + serverTime + "}");
    }

    @Test
    void correctsClockOffsetOnceAndRepeats() throws Exception {
        long serverTime = now() + 3600;
        server.enqueue(timestampOutOfRange(serverTime));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").maxRetries(0).build())) {
            assertEquals("abc", client.payouts().info("abc").uuid());
            client.payouts().info("abc");
        }
        assertEquals(3, server.getRequestCount());
        RecordedRequest first = server.takeRequest();
        RecordedRequest repeated = server.takeRequest();
        RecordedRequest next = server.takeRequest();

        assertTrue(Math.abs(Long.parseLong(first.getHeader(RequestSigner.HEADER_TIMESTAMP)) - now()) <= 5);
        assertTrue(Math.abs(Long.parseLong(repeated.getHeader(RequestSigner.HEADER_TIMESTAMP)) - serverTime) <= 5);
        assertTrue(Math.abs(Long.parseLong(next.getHeader(RequestSigner.HEADER_TIMESTAMP)) - serverTime) <= 5);
        assertNotEquals(first.getHeader(RequestSigner.HEADER_NONCE), repeated.getHeader(RequestSigner.HEADER_NONCE));
        assertHmacValid(repeated, "/v1/payout/info", "");
    }

    @Test
    void clockCorrectionIsNotRepeated() {
        server.enqueue(timestampOutOfRange(now() + 3600));
        server.enqueue(timestampOutOfRange(now() + 7200));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE, ex.code());
            assertEquals(401, ex.status());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void noRepeatWithoutServerTime() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"ok\":false,\"error\":\"SIGNATURE_TIMESTAMP_OUT_OF_RANGE\",\"msg\":\"out of range\"}"));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE, ex.code());
        }
        assertEquals(1, server.getRequestCount());
    }

    private static MockResponse whiteLabelRefusal(int status, String name, String code, String message,
                                                  String serverTimeField) {
        String extra = serverTimeField.isEmpty() ? "" : "," + serverTimeField;
        return new MockResponse().setResponseCode(status).setBody("{\"data\":null,\"error\":{"
                + "\"status\":" + status + ",\"name\":\"" + name + "\",\"message\":\"" + message + "\","
                + "\"details\":{\"code\":\"" + code + "\"" + extra + "}}" + extra + "}");
    }

    private static MockResponse whiteLabelTimestampOutOfRange(long serverTime) {
        return whiteLabelRefusal(401, "UnauthorizedError", ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE,
                "X-CC-Timestamp differs from server time by more than 300 seconds",
                "\"server_time\":" + serverTime);
    }

    @Test
    void whiteLabelEnvelopeCorrectsClockOnceAndRepeats() throws Exception {
        long serverTime = now() - 3600;
        server.enqueue(whiteLabelTimestampOutOfRange(serverTime));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").maxRetries(0).build())) {
            assertEquals("abc", client.payouts().info("abc").uuid());
        }
        assertEquals(2, server.getRequestCount());
        RecordedRequest first = server.takeRequest();
        RecordedRequest repeated = server.takeRequest();

        assertTrue(Math.abs(Long.parseLong(first.getHeader(RequestSigner.HEADER_TIMESTAMP)) - now()) <= 5);
        assertTrue(Math.abs(Long.parseLong(repeated.getHeader(RequestSigner.HEADER_TIMESTAMP)) - serverTime) <= 5);
        assertNotEquals(first.getHeader(RequestSigner.HEADER_NONCE), repeated.getHeader(RequestSigner.HEADER_NONCE));
        assertHmacValid(repeated, "/v1/payout/info", "");
    }

    @Test
    void whiteLabelServerTimeOnlyInDetailsIsUsed() throws Exception {
        long serverTime = now() + 1800;
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"data\":null,\"error\":{"
                + "\"status\":401,\"name\":\"UnauthorizedError\",\"message\":\"out of range\","
                + "\"details\":{\"code\":\"SIGNATURE_TIMESTAMP_OUT_OF_RANGE\",\"server_time\":" + serverTime + "}}}"));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").maxRetries(0).build())) {
            client.payouts().info("abc");
        }
        assertEquals(2, server.getRequestCount());
        server.takeRequest();
        RecordedRequest repeated = server.takeRequest();
        assertTrue(Math.abs(Long.parseLong(repeated.getHeader(RequestSigner.HEADER_TIMESTAMP)) - serverTime) <= 5);
    }

    @Test
    void whiteLabelClockCorrectionIsNotRepeated() {
        server.enqueue(whiteLabelTimestampOutOfRange(now() + 3600));
        server.enqueue(whiteLabelTimestampOutOfRange(now() + 7200));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE, ex.code());
            assertEquals(401, ex.status());
            assertEquals("X-CC-Timestamp differs from server time by more than 300 seconds", ex.description());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void whiteLabelNoRepeatWithoutServerTime() {
        server.enqueue(whiteLabelRefusal(401, "UnauthorizedError", ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE,
                "out of range", ""));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE, ex.code());
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void whiteLabelSignatureRefusalsCarryTheirCodes() {
        server.enqueue(whiteLabelRefusal(400, "BadRequestError", ErrorCode.BAD_AUTH_HEADERS, "bad X-CC-Nonce", ""));
        server.enqueue(whiteLabelRefusal(401, "UnauthorizedError", ErrorCode.INVALID_SIGNATURE, "Invalid signature", ""));
        server.enqueue(whiteLabelRefusal(401, "UnauthorizedError", ErrorCode.SIGNATURE_REPLAYED,
                "X-CC-Nonce has already been used", ""));
        server.enqueue(whiteLabelRefusal(413, "PayloadTooLargeError", ErrorCode.PAYLOAD_TOO_LARGE,
                "request body too large", ""));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            ApiException badHeaders = assertThrows(ApiException.class, () -> client.payouts().info("a"));
            assertEquals("BAD_AUTH_HEADERS", badHeaders.code());
            assertEquals(400, badHeaders.status());
            assertEquals("bad X-CC-Nonce", badHeaders.description());

            ApiException invalid = assertThrows(ApiException.class, () -> client.payouts().info("b"));
            assertEquals(ErrorCode.INVALID_SIGNATURE, invalid.code());
            assertEquals(401, invalid.status());

            ApiException replayed = assertThrows(ApiException.class, () -> client.payouts().info("c"));
            assertEquals(ErrorCode.SIGNATURE_REPLAYED, replayed.code());

            ApiException tooLarge = assertThrows(ApiException.class, () -> client.payouts().info("d"));
            assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, tooLarge.code());
            assertEquals(413, tooLarge.status());
            assertTrue(tooLarge.raw().contains("\"details\""));
        }
        assertEquals(4, server.getRequestCount());
    }

    @Test
    void replayedAndPayloadTooLargeAreNotRetried() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"ok\":false,\"error\":\"SIGNATURE_REPLAYED\",\"msg\":\"X-CC-Nonce has already been used\"}"));
        server.enqueue(new MockResponse().setResponseCode(413)
                .setBody("{\"ok\":false,\"error\":\"PAYLOAD_TOO_LARGE\",\"msg\":\"request body exceeds 4096 bytes\"}"));
        try (CryptoChiefClient client = new CryptoChiefClient(options("/").build())) {
            ApiException replayed = assertThrows(ApiException.class, () -> client.payouts().info("a"));
            assertEquals(ErrorCode.SIGNATURE_REPLAYED, replayed.code());
            assertEquals(401, replayed.status());

            ApiException tooLarge = assertThrows(ApiException.class, () -> client.payouts().info("b"));
            assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, tooLarge.code());
            assertEquals(413, tooLarge.status());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void signatureErrorCodesMatchTheGateway() {
        assertEquals("BAD_AUTH_HEADERS", ErrorCode.BAD_AUTH_HEADERS);
        assertEquals("SIGNATURE_TIMESTAMP_OUT_OF_RANGE", ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE);
        assertEquals("INVALID_SIGNATURE", ErrorCode.INVALID_SIGNATURE);
        assertEquals("SIGNATURE_REPLAYED", ErrorCode.SIGNATURE_REPLAYED);
        assertEquals("PAYLOAD_TOO_LARGE", ErrorCode.PAYLOAD_TOO_LARGE);
    }
}
