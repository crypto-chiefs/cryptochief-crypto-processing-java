package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.exceptions.NetworkException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Retries: HTTP 502, 503, 504 and network errors. HTTP 500, every other status and 4xx: one attempt. */
class RetryPolicyTest {

    private static final int MAX_RETRIES = 2;
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

    private Options.Builder options() {
        return Options.builder()
                .merchantId("mer_test")
                .apiKey("secret-key")
                .baseUrl(server.url("/").toString())
                .maxRetries(MAX_RETRIES)
                .initialRetryDelay(Duration.ofMillis(1))
                .maxRetryDelay(Duration.ofMillis(5));
    }

    private static MockResponse failure(int status) {
        return new MockResponse().setResponseCode(status)
                .setBody("{\"ok\":false,\"error\":\"INTERNAL_ERROR\",\"msg\":\"internal error\"}");
    }

    private void enqueueFailures(int status) {
        for (int i = 0; i <= MAX_RETRIES; i++) {
            server.enqueue(failure(status));
        }
    }

    @Test
    void a500IsAttemptedOnce() {
        enqueueFailures(500);
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options().build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(500, ex.status());
            assertEquals("INTERNAL_ERROR", ex.code());
            assertEquals("internal error", ex.description());
            assertFalse(ex.retryable());
        }
        assertEquals(1, server.getRequestCount());
    }

    /** A white-label install answers 500 with code NETWORK_ERROR when its own call fails. */
    @Test
    void a500NamingNetworkErrorIsAttemptedOnce() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody(
                "{\"data\":null,\"error\":{\"status\":500,\"name\":\"ApplicationError\","
                        + "\"message\":\"provider error: NETWORK_ERROR\",\"details\":{\"code\":\"NETWORK_ERROR\"}}}"));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options().build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(500, ex.status());
            assertEquals(ErrorCode.NETWORK_ERROR, ex.code());
            assertFalse(ex.retryable());
        }
        assertEquals(1, server.getRequestCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void retriedStatusUsesTheWholeBudget(int status) {
        enqueueFailures(status);
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options().build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(status, ex.status());
            assertEquals("INTERNAL_ERROR", ex.code());
            assertTrue(ex.retryable());
        }
        assertEquals(MAX_RETRIES + 1, server.getRequestCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void retriedStatusThenSuccessReturnsTheAnswer(int status) {
        server.enqueue(failure(status));
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        try (CryptoChiefClient client = new CryptoChiefClient(options().build())) {
            assertEquals("abc", client.payouts().info("abc").uuid());
        }
        assertEquals(2, server.getRequestCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422, 429, 500, 501, 505, 507, 520, 524, 599})
    void otherStatusesAreAttemptedOnce(int status) {
        enqueueFailures(status);
        try (CryptoChiefClient client = new CryptoChiefClient(options().build())) {
            ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
            assertEquals(status, ex.status());
            assertFalse(ex.retryable());
        }
        assertEquals(1, server.getRequestCount());
    }

    /**
     * {@code retryable()} is true exactly for the statuses the client retries. 407 and 408 are left out:
     * OkHttp handles them itself before the SDK sees a response.
     */
    @Test
    void retryableMatchesWhatTheClientRetries() {
        AtomicInteger current = new AtomicInteger();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return failure(current.get());
            }
        });
        try (CryptoChiefClient client = new CryptoChiefClient(options().build())) {
            for (int status = 400; status <= 599; status++) {
                if (status == 407 || status == 408) continue;
                current.set(status);
                int before = server.getRequestCount();
                ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
                int attempts = server.getRequestCount() - before;
                boolean retried = status == 502 || status == 503 || status == 504;

                assertEquals(status, ex.status());
                assertEquals(retried, ex.retryable(), "retryable() for " + status);
                assertEquals(retried ? MAX_RETRIES + 1 : 1, attempts, "attempts for " + status);
            }
        }
    }

    @Test
    void retryableHelper() {
        assertFalse(new ApiException("INTERNAL_ERROR", 500, "internal error", "").retryable());
        assertTrue(new ApiException("HTTP_502", 502, "HTTP_502", "").retryable());
        assertTrue(new ApiException("HTTP_503", 503, "HTTP_503", "").retryable());
        assertTrue(new ApiException("HTTP_504", 504, "HTTP_504", "").retryable());
        assertFalse(new ApiException("HTTP_501", 501, "HTTP_501", "").retryable());
        assertFalse(new ApiException("HTTP_505", 505, "HTTP_505", "").retryable());
        assertFalse(new ApiException(ErrorCode.RATE_LIMITED, 429, ErrorCode.RATE_LIMITED, "").retryable());
        assertTrue(new ApiException(ErrorCode.NETWORK_ERROR, 0, ErrorCode.NETWORK_ERROR, "").retryable());
        assertFalse(new ApiException(ErrorCode.NETWORK_ERROR, 500, ErrorCode.NETWORK_ERROR, "{}").retryable());
    }

    /** Fails the first {@code failures} calls with {@code error} before they reach the server. */
    private static OkHttpClient failingClient(int failures, AtomicInteger calls, IOException error) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if (calls.incrementAndGet() <= failures) throw error;
                    return chain.proceed(chain.request());
                })
                .build();
    }

    @Test
    void aNetworkErrorIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        OkHttpClient http = failingClient(1, calls, new IOException("connection reset"));
        try (CryptoChiefClient client = new CryptoChiefClient(options().httpClient(http).build())) {
            assertEquals("abc", client.payouts().info("abc").uuid());
        }
        assertEquals(2, calls.get());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void networkErrorsUseTheWholeBudget() {
        AtomicInteger calls = new AtomicInteger();
        OkHttpClient http = failingClient(Integer.MAX_VALUE, calls,
                new SocketTimeoutException("timeout"));
        try (CryptoChiefClient client = new CryptoChiefClient(options().httpClient(http).build())) {
            NetworkException ex = assertThrows(NetworkException.class, () -> client.payouts().info("abc"));
            assertTrue(ex.getMessage().contains("timeout"), ex.getMessage());
        }
        assertEquals(MAX_RETRIES + 1, calls.get());
        assertEquals(0, server.getRequestCount());
    }

    /** A 200 whose body cannot be read. */
    private static ResponseBody unreadableBody() {
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public BufferedSource source() {
                return Okio.buffer(new Source() {
                    @Override
                    public long read(Buffer sink, long byteCount) throws IOException {
                        throw new IOException("unexpected end of stream");
                    }

                    @Override
                    public Timeout timeout() {
                        return Timeout.NONE;
                    }

                    @Override
                    public void close() {
                    }
                });
            }
        };
    }

    @Test
    void aResponseBodyReadFailureIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        server.enqueue(new MockResponse().setBody(PAYOUT_OK));
        OkHttpClient http = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if (calls.incrementAndGet() == 1) {
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(unreadableBody())
                                .build();
                    }
                    return chain.proceed(chain.request());
                })
                .build();
        try (CryptoChiefClient client = new CryptoChiefClient(options().httpClient(http).build())) {
            assertEquals("abc", client.payouts().info("abc").uuid());
        }
        assertEquals(2, calls.get());
        assertEquals(1, server.getRequestCount());
    }
}
