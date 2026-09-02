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

    /**
     * The gateway decides some refusals itself and names them in {@code error}, putting an
     * English sentence in {@code msg}. Reading the code out of {@code msg} would hand the
     * caller that sentence and leave every gateway-side constant unmatchable.
     */
    @Test
    void gatewayEnvelopePutsTheMachineCodeInCode() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody(
                "{\"ok\":false,\"error\":\"LABEL_TOO_LONG\","
                        + "\"msg\":\"label is longer than 255 characters\"}"));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.wallets().setLabel("0xabc", "x".repeat(300)));

        assertEquals(ErrorCode.LABEL_TOO_LONG, ex.code());
        assertEquals("label is longer than 255 characters", ex.description());
        assertTrue(ex.getMessage().contains("label is longer than 255 characters"));
        assertTrue(ex.raw().contains("LABEL_TOO_LONG"));
        assertTrue(ex.raw().contains("label is longer than 255 characters"));
    }

    /** The switch the documentation tells callers to write has to select the right branch. */
    @Test
    void gatewayCodeMatchesTheErrorCodeConstantInASwitch() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody(
                "{\"ok\":false,\"error\":\"LABEL_TOO_LONG\","
                        + "\"msg\":\"label is longer than 255 characters\"}"));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.wallets().setLabel("0xabc", "x".repeat(300)));

        String branch = switch (ex.code()) {
            case ErrorCode.LABEL_TOO_LONG -> "label-too-long";
            case ErrorCode.INVALID_PARAMS -> "invalid-params";
            default -> "unmatched";
        };
        assertEquals("label-too-long", branch);
    }

    /** Relayed upstream refusals keep naming themselves in {@code msg}. */
    @Test
    void upstreamEnvelopeLiftsTheMsgTokenIntoCode() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody(
                "{\"ok\":false,\"error\":\"SERVICE_ERROR\",\"msg\":\"wallet_not_found\"}"));
        ApiException ex = assertThrows(ApiException.class,
                () -> client.wallets().rebindMaster("0xabc", "0xdef"));

        assertEquals("wallet_not_found", ex.code());
        assertEquals("wallet_not_found", ex.description());
        assertEquals(404, ex.status());
        assertTrue(ex.raw().contains("SERVICE_ERROR"));
    }

    /** Nothing usable in {@code msg}: the generic marker is still better than nothing. */
    @Test
    void serviceErrorWithoutAMsgTokenKeepsTheGenericMarker() {
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"ok\":false,\"error\":\"SERVICE_ERROR\",\"msg\":\"\"}"));
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"ok\":false,\"error\":\"SERVICE_ERROR\",\"msg\":\"\"}"));
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"ok\":false,\"error\":\"SERVICE_ERROR\",\"msg\":\"\"}"));
        ApiException ex = assertThrows(ApiException.class, () -> client.payouts().info("d"));
        assertEquals(ErrorCode.SERVICE_ERROR, ex.code());
    }
}
