package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.models.NativeBuyRequest;
import com.cryptochief.processing.models.NativeOrder;
import com.cryptochief.processing.models.NativeOrderStatus;
import com.cryptochief.processing.models.NativeQuote;
import com.cryptochief.processing.models.NativeQuoteRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeServiceTest {

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

    private static String quoteJson() {
        return """
                {"ref": "nq-abc", "network": "TRON_MAINNET", "receive_address": "TRecipient",
                 "amount": "0.05", "coin_price_usd": "0.0146", "transfer_fee": "0.000278",
                 "transfer_fee_usd": "0.0001", "subtotal_usd": "0.0147",
                 "total_usd": "0.0191", "credits": 191000, "coin_usd": "0.29250000",
                 "expires_at": "2026-09-18T10:01:30Z", "expires_in_sec": 90}
                """;
    }

    private static String deliveredOrderJson() {
        return """
                {"id": 321, "idempotency_key": "buy-1", "status": "delivered",
                 "network": "TRON_MAINNET", "receive_address": "TRecipient", "amount": "0.05",
                 "tx_hash": "ab12cd", "transfer_fee": "0.000278", "transfer_fee_usd": "0.0001",
                 "coin_price_usd": "0.0146", "total_usd": "0.0191", "credits": 191000,
                 "coin_usd": "0.29250000", "settled": true, "needs_attention": false,
                 "created_at": "2026-09-18T10:00:00Z", "delivered_at": "2026-09-18T10:00:04Z"}
                """;
    }

    @Test
    void quotePostsTheSignedBodyAndMapsThePriceBuildup() throws Exception {
        server.enqueue(new MockResponse().setBody(quoteJson()));
        NativeQuote q = client.nativeCoin().quote(
                NativeQuoteRequest.of(Chain.TRON_MAINNET, "TRecipient", "0.05"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/native/quote", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"amount\":\"0.05\",\"network\":\"TRON_MAINNET\",\"receive_address\":\"TRecipient\"}",
                body);
        assertSigned(recorded, body, null);
        assertEquals("nq-abc", q.ref());
        assertEquals(Chain.TRON_MAINNET, q.network());
        assertEquals("TRecipient", q.receiveAddress());
        assertEquals("0.05", q.amount());
        assertEquals("0.0146", q.coinPriceUsd());
        assertEquals("0.000278", q.transferFee());
        assertEquals("0.0001", q.transferFeeUsd());
        assertEquals("0.0147", q.subtotalUsd());
        assertEquals("0.0191", q.totalUsd());
        assertEquals(191000L, q.credits());
        assertEquals("0.29250000", q.coinUsd());
        assertEquals("2026-09-18T10:01:30Z", q.expiresAt());
        assertEquals(90L, q.expiresInSec());
    }

    @Test
    void buyWithoutAnIdempotencyKeyIsRefusedBeforeAnyRequestGoesOut() {
        assertThrows(IllegalArgumentException.class, () -> client.nativeCoin().buy(
                NativeBuyRequest.of(Chain.TRON_MAINNET, "TRecipient", "0.05")));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void buySendsTheSignedIdempotencyKeyHeaderAndMapsTheDeliveredOrder() throws Exception {
        server.enqueue(new MockResponse().setBody(deliveredOrderJson()));
        NativeOrder o = client.withIdempotencyKey("buy-1").nativeCoin().buy(
                NativeBuyRequest.of(Chain.TRON_MAINNET, "TRecipient", "0.05"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/native/buy", recorded.getPath());
        assertEquals("buy-1", recorded.getHeader(RequestSigner.HEADER_IDEMPOTENCY_KEY));
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"amount\":\"0.05\",\"network\":\"TRON_MAINNET\",\"receive_address\":\"TRecipient\"}",
                body);
        assertSigned(recorded, body, "buy-1");
        assertEquals(321L, o.id());
        assertEquals("buy-1", o.idempotencyKey());
        assertEquals(NativeOrderStatus.DELIVERED, o.status());
        assertEquals(Chain.TRON_MAINNET, o.network());
        assertEquals("TRecipient", o.receiveAddress());
        assertEquals("0.05", o.amount());
        assertEquals("ab12cd", o.txHash());
        assertEquals("0.000278", o.transferFee());
        assertEquals("0.0001", o.transferFeeUsd());
        assertEquals("0.0146", o.coinPriceUsd());
        assertEquals("0.0191", o.totalUsd());
        assertEquals(191000L, o.credits());
        assertEquals("0.29250000", o.coinUsd());
        assertTrue(o.settled());
        assertFalse(o.needsAttention());
        assertNull(o.error());
        assertEquals("2026-09-18T10:00:00Z", o.createdAt());
        assertEquals("2026-09-18T10:00:04Z", o.deliveredAt());
    }

    @Test
    void buyWithAQuoteRefPostsTheRefAlone() throws Exception {
        server.enqueue(new MockResponse().setBody(deliveredOrderJson()));
        client.withIdempotencyKey("buy-1").nativeCoin().buy(NativeBuyRequest.ofQuote("nq-abc"));
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"quote_ref\":\"nq-abc\"}", body);
        assertSigned(recorded, body, "buy-1");
    }

    @Test
    void aRefusedOrderComesBackFromA502WithNoChargeAndNoDelivery() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(502).setBody("""
                {"id": 322, "idempotency_key": "buy-2", "status": "refused",
                 "network": "TRON_MAINNET", "receive_address": "TRecipient", "amount": "0.05",
                 "transfer_fee": "", "transfer_fee_usd": "0.00", "coin_price_usd": "0.00",
                 "coin_usd": "",
                 "settled": true, "needs_attention": false,
                 "error_code": "INSUFFICIENT_LIQUIDITY",
                 "error": "we cannot fund that sale from our own wallet right now; nothing was bought and nothing was charged",
                 "created_at": "2026-09-18T10:00:00Z"}
                """));
        // 502 retries are pointless here - the order is settled. retries: 0.
        NativeOrder o;
        try (CryptoChiefClient noRetry = noRetryClient()) {
            o = noRetry.withIdempotencyKey("buy-2").nativeCoin().buy(
                    NativeBuyRequest.of(Chain.TRON_MAINNET, "TRecipient", "0.05"));
        }
        server.takeRequest();
        assertEquals(NativeOrderStatus.REFUSED, o.status());
        assertTrue(o.settled());
        assertFalse(o.needsAttention());
        assertEquals("INSUFFICIENT_LIQUIDITY", o.errorCode());
        assertEquals("we cannot fund that sale from our own wallet right now;"
                + " nothing was bought and nothing was charged", o.error());
        // A refusal that never got priced still carries the price fields - as "" or "0.00",
        // not null; only what was never sent or charged is omitted.
        assertEquals("", o.transferFee());
        assertEquals("0.00", o.transferFeeUsd());
        assertEquals("0.00", o.coinPriceUsd());
        assertEquals("", o.coinUsd());
        assertNull(o.txHash());
        assertNull(o.totalUsd());
        assertNull(o.credits());
        assertNull(o.deliveredAt());
    }

    @Test
    void anUnresolvedOrderComesBackFromA409NeedingAttention() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("""
                {"id": 323, "idempotency_key": "buy-3", "status": "unresolved",
                 "network": "TRON_MAINNET", "receive_address": "TRecipient", "amount": "0.05",
                 "transfer_fee": "0.000278", "transfer_fee_usd": "0.0001",
                 "coin_price_usd": "0.0146", "total_usd": "0.0191", "credits": 191000,
                 "coin_usd": "0.29250000",
                 "settled": false, "needs_attention": true,
                 "error_code": "SEND_UNKNOWN",
                 "error": "the transfer's outcome never came back, so the order will not be retried - it may already have been sent; ask us to check it",
                 "created_at": "2026-09-18T10:00:00Z"}
                """));
        // 409 + needs_attention: do NOT retry; follow the order with order().
        NativeOrder o = client.withIdempotencyKey("buy-3").nativeCoin().buy(
                NativeBuyRequest.of(Chain.TRON_MAINNET, "TRecipient", "0.05"));
        server.takeRequest();
        assertEquals(NativeOrderStatus.UNRESOLVED, o.status());
        assertFalse(o.settled());
        assertTrue(o.needsAttention());
        assertEquals("SEND_UNKNOWN", o.errorCode());
        // charged already, so the billing fields are facts, not nulls
        assertEquals("0.0191", o.totalUsd());
        assertEquals(191000L, o.credits());
    }

    @Test
    void aRefusalForInsufficientCreditsComesBackFromA402AsTheOrder() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(402).setBody("""
                {"id": 324, "idempotency_key": "buy-4", "status": "refused",
                 "network": "TRON_MAINNET", "receive_address": "TRecipient", "amount": "0.05",
                 "transfer_fee": "", "transfer_fee_usd": "0.00", "coin_price_usd": "0.00",
                 "coin_usd": "",
                 "settled": true, "needs_attention": false,
                 "error_code": "INSUFFICIENT_CREDITS",
                 "error": "your credit balance did not cover this order; nothing was bought and nothing was charged",
                 "created_at": "2026-09-18T10:00:00Z"}
                """));
        NativeOrder o = client.withIdempotencyKey("buy-4").nativeCoin().buy(
                NativeBuyRequest.of(Chain.TRON_MAINNET, "TRecipient", "0.05"));
        server.takeRequest();
        assertEquals(NativeOrderStatus.REFUSED, o.status());
        assertEquals("INSUFFICIENT_CREDITS", o.errorCode());
        assertNull(o.credits());
    }

    @Test
    void anErrorEnvelopeThrowsWithTheEnvelopeCode() {
        server.enqueue(new MockResponse().setResponseCode(409).setBody(
                "{\"ok\":false,\"error\":\"QUOTE_EXPIRED\","
                        + "\"msg\":\"that quote has expired; ask for a new price\"}"));
        ApiException e = assertThrows(ApiException.class, () -> client.withIdempotencyKey("buy-5")
                .nativeCoin().buy(NativeBuyRequest.ofQuote("nq-abc")));
        assertEquals("QUOTE_EXPIRED", e.code());
        assertEquals(409, e.status());
        assertEquals("that quote has expired; ask for a new price", e.description());
    }

    @Test
    void orderLooksUpByTheIdempotencyKey() throws Exception {
        server.enqueue(new MockResponse().setBody(deliveredOrderJson()));
        NativeOrder o = client.nativeCoin().order("buy-1");
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/native/order", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"key\":\"buy-1\"}", body);
        assertSigned(recorded, body, null);
        assertEquals("buy-1", o.idempotencyKey());
        assertEquals(NativeOrderStatus.DELIVERED, o.status());
    }

    @Test
    void orderRejectsAnEmptyKeyBeforeAnyRequestGoesOut() {
        assertThrows(IllegalArgumentException.class, () -> client.nativeCoin().order(""));
        assertEquals(0, server.getRequestCount());
    }

    private CryptoChiefClient noRetryClient() {
        return new CryptoChiefClient(Options.builder()
                .merchantId("mer_test")
                .apiKey("secret-key")
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .maxRetries(0)
                .build());
    }

    private static void assertSigned(RecordedRequest recorded, String body, String idempotencyKey) {
        assertNull(recorded.getHeader("Signature"));
        String expected = RequestSigner.signHmacV1("secret-key", recorded.getHeader(RequestSigner.HEADER_TIMESTAMP),
                recorded.getHeader(RequestSigner.HEADER_NONCE), recorded.getMethod(),
                recorded.getRequestUrl().encodedPath(), "", "mer_test", idempotencyKey,
                body.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, recorded.getHeader(RequestSigner.HEADER_HMAC_SIGNATURE));
    }
}
