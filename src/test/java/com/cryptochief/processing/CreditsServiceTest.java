package com.cryptochief.processing;

import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.models.CreditsTopupRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditsServiceTest {

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
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.shutdown();
    }

    @Test
    void balancePostsSignedEmptyBody() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"credits_balance\":42000000,\"usd_balance\":\"4.20\","
                        + "\"is_postpaid\":false,\"debt_limit_credits\":0,"
                        + "\"can_execute_gas_operations\":true,\"gas_ops_min_credits\":3000000,"
                        + "\"timestamp\":\"2026-08-18T12:00:00Z\"}"));
        client.credits().balance();
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/credits/balance", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        assertEquals("{}", recorded.getBody().readUtf8());
        assertSigned(recorded, "{}");
    }

    @Test
    void balanceMapsAllFieldsIncludingNegativeUsdBalance() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"credits_balance\":-15200000,\"usd_balance\":\"-1.52\","
                        + "\"is_postpaid\":true,\"debt_limit_credits\":500000000,"
                        + "\"can_execute_gas_operations\":false,\"gas_ops_min_credits\":3000000,"
                        + "\"timestamp\":\"2026-08-18T12:00:00Z\"}"));
        var balance = client.credits().balance();
        assertEquals(-15200000L, balance.creditsBalance());
        assertEquals("-1.52", balance.usdBalance());
        assertTrue(balance.isPostpaid());
        assertEquals(500000000L, balance.debtLimitCredits());
        assertFalse(balance.canExecuteGasOperations());
        assertEquals(3000000L, balance.gasOpsMinCredits());
        assertEquals("2026-08-18T12:00:00Z", balance.timestamp());
    }

    @Test
    void topupPostsSignedBodyOmittingEmptyOptionalUrls() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"invoice_id\":9001,\"payment_link\":\"https://pay.crypto-chief.com/topup/abc\","
                        + "\"amount\":\"25\",\"currency\":\"USDT\",\"status\":\"pending\"}"));
        var topup = client.credits().topup(CreditsTopupRequest.of("25", "USDT"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/credits/topup", recorded.getPath());
        assertEquals("mer_test", recorded.getHeader("Merchant"));
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"amount\":\"25\",\"currency\":\"USDT\"}", body);
        assertSigned(recorded, body);
        assertEquals(9001L, topup.invoiceId());
        assertEquals("https://pay.crypto-chief.com/topup/abc", topup.paymentLink());
        assertEquals("25", topup.amount());
        assertEquals("USDT", topup.currency());
        assertEquals("pending", topup.status());
        assertNull(topup.orderUuid());
        assertNull(topup.expiredAt());
    }

    @Test
    void topupSendsOptionalUrlsAndMapsAllFields() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"invoice_id\":9002,\"payment_link\":\"https://pay.crypto-chief.com/topup/def\","
                        + "\"amount\":\"100000\",\"currency\":\"USDC\",\"status\":\"pending\","
                        + "\"order_uuid\":\"5f1e0c9a-7f3b-4c2d-9d68-1c2c3d4e5f60\",\"expired_at\":1755529200}"));
        var topup = client.credits().topup(new CreditsTopupRequest(
                "100000", "USDC", "https://your.app/topup/ok", "https://your.app/topup/fail"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/credits/topup", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"amount\":\"100000\",\"currency\":\"USDC\","
                + "\"url_error\":\"https://your.app/topup/fail\","
                + "\"url_success\":\"https://your.app/topup/ok\"}", body);
        assertSigned(recorded, body);
        assertEquals(9002L, topup.invoiceId());
        assertEquals("https://pay.crypto-chief.com/topup/def", topup.paymentLink());
        assertEquals("100000", topup.amount());
        assertEquals("USDC", topup.currency());
        assertEquals("pending", topup.status());
        assertEquals("5f1e0c9a-7f3b-4c2d-9d68-1c2c3d4e5f60", topup.orderUuid());
        assertEquals(1755529200L, topup.expiredAt());
    }

    private static void assertSigned(RecordedRequest recorded, String body) {
        assertNull(recorded.getHeader("Signature"));
        String expected = RequestSigner.signHmacV1("secret-key", recorded.getHeader(RequestSigner.HEADER_TIMESTAMP),
                recorded.getHeader(RequestSigner.HEADER_NONCE), recorded.getMethod(),
                recorded.getRequestUrl().encodedPath(), "", "mer_test", null,
                body.getBytes(StandardCharsets.UTF_8));
        assertEquals("v1=" + expected, recorded.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE));
    }
}
