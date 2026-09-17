package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.models.ExecutePayoutRequest;
import com.cryptochief.processing.models.PayoutInfo;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client against a mock gateway that accepts only HMAC v1: headers checked as the gateway checks them,
 * signature recomputed from the received bytes, the {@code Signature} header never read.
 */
class HmacV1GatewayMockTest {

    private static final String MERCHANT = "mer_mock";
    private static final String API_KEY = "mock_api_key";
    private static final String PAYOUT_OK = "{\"uuid\":\"abc\",\"status\":\"paid\",\"network\":\"ETH_MAINNET\","
            + "\"coin\":\"ETH\",\"amount\":\"1\",\"to_address\":\"0x\"}";

    private MockWebServer server;
    private final List<RecordedRequest> accepted = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return gateway(request);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private MockResponse gateway(RecordedRequest request) {
        String merchant = request.getHeader("Merchant");
        String timestamp = request.getHeader("X-CC-Timestamp");
        String nonce = request.getHeader("X-CC-Nonce");
        String signature = request.getHeader("X-CC-Signature");
        if (merchant == null || timestamp == null || !timestamp.matches("[0-9]+")
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,64}")
                || signature == null || !signature.matches("v1=[0-9a-fA-F]{64}")) {
            return refusal(400, ErrorCode.BAD_AUTH_HEADERS);
        }
        if (Math.abs(Long.parseLong(timestamp) - System.currentTimeMillis() / 1000L) > 300) {
            return refusal(401, ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE);
        }
        byte[] body = request.getBody().clone().readByteArray();
        if (body.length > 0 && !String.valueOf(request.getHeader("Content-Type")).startsWith("application/json")) {
            return refusal(400, ErrorCode.INVALID_PARAMS);
        }
        String query = request.getRequestUrl().encodedQuery();
        String idempotencyKey = request.getHeader("Idempotency-Key");
        String stringToSign = String.join("\n", "CC-HMAC-SHA256-REQ-V1", timestamp, nonce,
                upperAscii(request.getMethod()), routePath(request), query == null ? "" : query, merchant,
                idempotencyKey == null ? "" : idempotencyKey, sha256Hex(body));
        if (!MERCHANT.equals(merchant) || !MessageDigest.isEqual(
                hmac(API_KEY, stringToSign), HexFormat.of().parseHex(signature.substring(3)))) {
            return refusal(401, ErrorCode.INVALID_SIGNATURE);
        }
        accepted.add(request);
        return new MockResponse().setBody(PAYOUT_OK);
    }

    /**
     * The route the gateway signs: the path percent-decoded, which is what a Go handler reads out of
     * {@code r.URL.Path}. {@code HttpUrl.pathSegments()} decodes each segment, so a {@code %2F} inside one
     * rejoins as a slash exactly as it does there.
     */
    private static String routePath(RecordedRequest request) {
        return "/" + String.join("/", request.getRequestUrl().pathSegments());
    }

    /** The gateway's ASCII upper-casing of the method token: {@code a-z} only. */
    private static String upperAscii(String method) {
        char[] out = method.toCharArray();
        for (int i = 0; i < out.length; i++) {
            if (out[i] >= 'a' && out[i] <= 'z') out[i] -= ('a' - 'A');
        }
        return new String(out);
    }

    private static MockResponse refusal(int status, String code) {
        return new MockResponse().setResponseCode(status)
                .setBody("{\"ok\":false,\"error\":\"" + code + "\",\"msg\":\"" + code + "\"}");
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Options.Builder options(String apiKey) {
        return Options.builder()
                .merchantId(MERCHANT)
                .apiKey(apiKey)
                .baseUrl(server.url("/").toString())
                .maxRetries(0);
    }

    @Test
    void clientRequestsPass() throws Exception {
        try (CryptoChiefClient client = new CryptoChiefClient(options(API_KEY).build())) {
            assertEquals("abc", client.payouts().info("abc").uuid());
            client.payouts().execute(new ExecutePayoutRequest("o-1", null, Chain.TRON_MAINNET, "USDT",
                    "10.50", "TXyz", null, null, null, null, null, null, null));
            client.credits().topup(null);
        }
        assertEquals(3, accepted.size());
        for (RecordedRequest r : accepted) {
            assertNull(r.getHeader("Signature"));
        }
        assertTrue(accepted.get(1).getBody().clone().readUtf8().contains("\"order_id\":\"o-1\""));
        assertEquals(0, accepted.get(2).getBodySize());
    }

    /** A signed request with a method of the caller's choosing; a GET carries a query and no body. */
    @Test
    void signedGetWithQueryPasses() throws Exception {
        try (CryptoChiefClient client = new CryptoChiefClient(options(API_KEY).build())) {
            assertEquals("abc", client.request("GET", "/v1/payout/info?uuid=abc&q=%D1%82%D0%B5%D1%81%D1%82",
                    null, PayoutInfo.class).uuid());
            client.request("get", "/v1/payout/info?uuid=abc", null, PayoutInfo.class);
        }
        assertEquals(2, accepted.size());
        for (RecordedRequest r : accepted) {
            assertEquals("GET", r.getMethod());
            assertEquals(0, r.getBodySize());
            assertNull(r.getHeader("Content-Type"));
        }
        assertEquals("uuid=abc&q=%D1%82%D0%B5%D1%81%D1%82", accepted.get(0).getRequestUrl().encodedQuery());
    }

    /** {@code %2F}, {@code %20} and non-ASCII: signed decoded, sent escaped. */
    @Test
    void percentEncodedAndNonAsciiPathsPass() throws Exception {
        try (CryptoChiefClient client = new CryptoChiefClient(options(API_KEY).build())) {
            client.request("POST", "/v1/orders/payout%2F8814%20x", Map.of(), PayoutInfo.class);
            client.request("POST", "/v1/заказ?q=%D1%82&r=a%2Fb", Map.of(),
                    PayoutInfo.class);
        }
        assertEquals(2, accepted.size());
        assertEquals("/v1/orders/payout%2F8814%20x", accepted.get(0).getRequestUrl().encodedPath());
        assertEquals("/v1/%D0%B7%D0%B0%D0%BA%D0%B0%D0%B7", accepted.get(1).getRequestUrl().encodedPath());
        assertEquals("q=%D1%82&r=a%2Fb", accepted.get(1).getRequestUrl().encodedQuery());
    }

    /** The key rides on every call the view makes and is covered by the signature. */
    @Test
    void idempotencyKeyIsSentAndSigned() throws Exception {
        try (CryptoChiefClient client = new CryptoChiefClient(options(API_KEY).build())) {
            client.withIdempotencyKey("payout-2026-09-16-0001").payouts().info("abc");
            client.withIdempotencyKey("payout-2026-09-16-0001")
                    .request("GET", "/v1/payout/info?uuid=abc", null, PayoutInfo.class);
            client.payouts().info("abc");
            client.withIdempotencyKey("").payouts().info("abc");
            client.withIdempotencyKey(null).payouts().info("abc");
        }
        assertEquals(5, accepted.size());
        assertEquals("payout-2026-09-16-0001", accepted.get(0).getHeader("Idempotency-Key"));
        assertEquals("payout-2026-09-16-0001", accepted.get(1).getHeader("Idempotency-Key"));
        for (int i = 2; i < 5; i++) {
            assertNull(accepted.get(i).getHeader("Idempotency-Key"), "request " + i);
        }
    }

    /** A key the server would trim or could not carry never reaches the wire. */
    @Test
    void unsendableIdempotencyKeyIsRefusedBeforeSending() {
        try (CryptoChiefClient client = new CryptoChiefClient(options(API_KEY).build())) {
            for (String key : new String[] {" leading", "trailing ", "\ttab", "tab\t", "line\nbreak",
                    "ключ", "bell"}) {
                assertThrows(IllegalArgumentException.class, () -> client.withIdempotencyKey(key), key);
            }
            assertDoesNotThrow(() -> client.withIdempotencyKey("mid space"));
        }
        assertEquals(0, accepted.size());
    }

    @Test
    void wrongKeyIsInvalidSignature() {
        try (CryptoChiefClient client = new CryptoChiefClient(options("other_key").build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(ErrorCode.INVALID_SIGNATURE, ex.code());
            assertEquals(401, ex.status());
        }
        assertEquals(0, accepted.size());
    }

    @Test
    void signatureHeaderWithoutHmacHeadersIsRefused() throws Exception {
        OkHttpClient http = new OkHttpClient();
        try {
            Request request = new Request.Builder()
                    .url(server.url("/v1/payout/info"))
                    .post(RequestBody.create("{\"uuid\":\"abc\"}".getBytes(StandardCharsets.UTF_8),
                            MediaType.get("application/json")))
                    .header("Merchant", MERCHANT)
                    .header("Signature", "0123456789abcdef0123456789abcdef")
                    .build();
            try (Response response = http.newCall(request).execute()) {
                assertEquals(400, response.code());
                assertTrue(response.body().string().contains(ErrorCode.BAD_AUTH_HEADERS));
            }
        } finally {
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }
        assertEquals(0, accepted.size());
    }
}
