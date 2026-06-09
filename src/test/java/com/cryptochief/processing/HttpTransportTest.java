package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransportTest {

    private MockWebServer server;
    private CryptoChiefClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new CryptoChiefClient(Options.builder()
                .merchantId("mer_test")
                .apiKey("secret-key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .maxRetries(2)
                .initialRetryDelay(Duration.ofMillis(1))
                .maxRetryDelay(Duration.ofMillis(5))
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.shutdown();
    }

    @Test
    void sendsMerchantSignatureHeadersAndSignsBody() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"uuid\":\"abc\",\"status\":\"paid\",\"network\":\"ETH_MAINNET\","
                        + "\"coin\":\"ETH\",\"amount\":\"1\",\"to_address\":\"0x\"}"));
        client.payouts().info("abc");
        RecordedRequest recorded = server.takeRequest();
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        assertNotNull(recorded.getHeader("Signature"));
        assertEquals("application/json", recorded.getHeader("Content-Type"));
        assertEquals("application/json", recorded.getHeader("Accept"));
        assertTrue(recorded.getHeader("User-Agent").startsWith("cryptochief-java/"));
        assertEquals("{\"uuid\":\"abc\"}", recorded.getBody().readUtf8());
    }

    @Test
    void retryOn5xx() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"SERVICE_ERROR\",\"msg\":\"try again\"}"));
        server.enqueue(new MockResponse().setBody(
                "{\"uuid\":\"abc\",\"status\":\"paid\",\"network\":\"ETH_MAINNET\","
                        + "\"coin\":\"ETH\",\"amount\":\"1\",\"to_address\":\"0x\"}"));
        var info = client.payouts().info("abc");
        assertEquals("abc", info.uuid());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void noRetryOn4xx() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"INVALID_PARAMS\"}"));
        ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("abc"));
        assertEquals(ErrorCode.INVALID_PARAMS, ex.code());
        assertEquals(400, ex.status());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void parsesErrorEnvelopeVariants() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"UNAUTHORIZED\"}"));
        ApiException ex1 = assertThrows(ApiException.class, () -> client.payouts().info("a"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex1.code());

        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"SERVICE_ERROR\",\"msg\":\"BATCH_EMPTY\"}"));
        ApiException ex2 = assertThrows(ApiException.class, () -> client.payouts().info("b"));
        assertEquals(ErrorCode.BATCH_EMPTY, ex2.code());

        server.enqueue(new MockResponse().setResponseCode(418).setBody("teapot"));
        ApiException ex3 = assertThrows(ApiException.class, () -> client.payouts().info("c"));
        assertEquals("HTTP_418", ex3.code());
        assertEquals(418, ex3.status());
    }
}
