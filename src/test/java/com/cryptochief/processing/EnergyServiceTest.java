package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.models.EnergyOrder;
import com.cryptochief.processing.models.EnergyOrderStatus;
import com.cryptochief.processing.models.EnergyQuote;
import com.cryptochief.processing.models.EnergyQuoteRequest;
import com.cryptochief.processing.models.EnergyRentRequest;
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

class EnergyServiceTest {

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
                {"ref": "q-abc", "receive_address": "TSender", "energy": 65000, "duration_sec": 3600,
                 "price_sun": 3900000, "price_trx": "3.900000", "price_usd": "1.14",
                 "credits": 11400000, "trx_usd": "0.29250000", "recipient_state": "cold",
                 "burn_price_sun": 9100000, "burn_price_trx": "9.100000", "burn_price_usd": "2.66",
                 "burn_price_credits": 26600000, "saving_trx": "5.200000", "saving_usd": "1.52",
                 "saving_credits": 15200000,
                 "expires_at": "2026-09-18T10:05:00Z", "expires_in_sec": 300}
                """;
    }

    private static String deliveredOrderJson() {
        return """
                {"id": 123, "idempotency_key": "rent-1", "status": "delivered",
                 "receive_address": "TSender", "energy": 65000, "duration_sec": 3600,
                 "price_sun": 3900000, "price_trx": "3.900000",
                 "price_usd": "1.14", "credits": 11400000, "trx_usd": "0.29250000",
                 "delivered_energy": 65000, "settled": true, "needs_attention": false,
                 "created_at": "2026-09-18T10:00:00Z", "delivered_at": "2026-09-18T10:00:05Z"}
                """;
    }

    @Test
    void quotePostsSignedBodyOmittingUnsetOptionals() throws Exception {
        server.enqueue(new MockResponse().setBody(quoteJson()));
        client.energy().quote(EnergyQuoteRequest.of("TSender"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/energy/quote", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"receive_address\":\"TSender\"}", body);
        assertSigned(recorded, body, null);
    }

    @Test
    void quoteMapsAllFieldsIncludingTheSavingAgainstBurning() throws Exception {
        server.enqueue(new MockResponse().setBody(quoteJson()));
        EnergyQuote q = client.energy().quote(EnergyQuoteRequest.of("TSender", 65000, 3600));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("{\"duration_sec\":3600,\"energy\":65000,\"receive_address\":\"TSender\"}",
                recorded.getBody().readUtf8());
        assertEquals("q-abc", q.ref());
        assertEquals("TSender", q.receiveAddress());
        assertEquals(65000L, q.energy());
        assertEquals(3600L, q.durationSec());
        assertEquals(3900000L, q.priceSun());
        assertEquals("3.900000", q.priceTrx());
        assertEquals("1.14", q.priceUsd());
        assertEquals(11400000L, q.credits());
        assertEquals("0.29250000", q.trxUsd());
        assertEquals("cold", q.recipientState());
        assertEquals(9100000L, q.burnPriceSun());
        assertEquals("9.100000", q.burnPriceTrx());
        assertEquals("2.66", q.burnPriceUsd());
        assertEquals(26600000L, q.burnPriceCredits());
        assertEquals("5.200000", q.savingTrx());
        assertEquals("1.52", q.savingUsd());
        assertEquals(15200000L, q.savingCredits());
        assertEquals("2026-09-18T10:05:00Z", q.expiresAt());
        assertEquals(300L, q.expiresInSec());
    }

    @Test
    void quoteLeavesTheDollarFieldsNullWhenNoRateIsAvailable() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"ref": "q-def", "receive_address": "TSender", "energy": 65000, "duration_sec": 3600,
                 "price_sun": 3900000, "price_trx": "3.900000", "recipient_state": "warm",
                 "burn_price_sun": 9100000, "burn_price_trx": "9.100000", "saving_trx": "5.200000",
                 "expires_at": "2026-09-18T10:05:00Z", "expires_in_sec": 300}
                """));
        EnergyQuote q = client.energy().quote(EnergyQuoteRequest.of("TSender"));
        assertNull(q.priceUsd());
        assertNull(q.credits());
        assertNull(q.trxUsd());
        assertNull(q.burnPriceUsd());
        assertNull(q.burnPriceCredits());
        assertNull(q.savingUsd());
        assertNull(q.savingCredits());
    }

    @Test
    void rentWithoutAnIdempotencyKeyIsRefusedBeforeAnyRequestGoesOut() {
        assertThrows(IllegalArgumentException.class, () -> client.energy().rent(
                EnergyRentRequest.of("TSender", 65000, 3600)));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void rentSendsTheSignedIdempotencyKeyHeaderAndMapsTheDeliveredOrder() throws Exception {
        server.enqueue(new MockResponse().setBody(deliveredOrderJson()));
        EnergyOrder o = client.withIdempotencyKey("rent-1").energy().rent(
                EnergyRentRequest.of("TSender", 65000, 3600));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/energy/rent", recorded.getPath());
        assertEquals("rent-1", recorded.getHeader(RequestSigner.HEADER_IDEMPOTENCY_KEY));
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"duration_sec\":3600,\"energy\":65000,\"receive_address\":\"TSender\"}", body);
        assertSigned(recorded, body, "rent-1");
        assertEquals(123L, o.id());
        assertEquals("rent-1", o.idempotencyKey());
        assertEquals(EnergyOrderStatus.DELIVERED, o.status());
        assertEquals("TSender", o.receiveAddress());
        assertEquals(65000L, o.energy());
        assertEquals(3600L, o.durationSec());
        assertEquals(3900000L, o.priceSun());
        assertEquals("3.900000", o.priceTrx());
        assertEquals("1.14", o.priceUsd());
        assertEquals(11400000L, o.credits());
        assertEquals("0.29250000", o.trxUsd());
        assertEquals(65000L, o.deliveredEnergy());
        assertTrue(o.settled());
        assertFalse(o.needsAttention());
        assertNull(o.error());
        assertEquals("2026-09-18T10:00:00Z", o.createdAt());
        assertEquals("2026-09-18T10:00:05Z", o.deliveredAt());
    }

    @Test
    void rentWithAQuoteRefPostsTheRefAlone() throws Exception {
        server.enqueue(new MockResponse().setBody(deliveredOrderJson()));
        client.withIdempotencyKey("rent-1").energy().rent(EnergyRentRequest.ofQuote("q-abc"));
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"quote_ref\":\"q-abc\"}", body);
        assertSigned(recorded, body, "rent-1");
    }

    @Test
    void aRefusedOrderComesBackFromA502WithNoBillingFieldsAndNoDelivery() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(502).setBody("""
                {"id": 124, "idempotency_key": "rent-2", "status": "refused",
                 "receive_address": "TSender", "energy": 65000, "duration_sec": 3600,
                 "price_sun": 3900000, "price_trx": "3.900000",
                 "settled": true, "needs_attention": false,
                 "error_code": "SUPPLIER_REFUSED",
                 "error": "no supplier could take this order; nothing was bought and nothing was charged",
                 "created_at": "2026-09-18T10:00:00Z"}
                """));
        // 502 retries are pointless here - the order is settled. retries: 0.
        EnergyOrder o;
        try (CryptoChiefClient noRetry = noRetryClient()) {
            o = noRetry.withIdempotencyKey("rent-2").energy().rent(
                    EnergyRentRequest.of("TSender", 65000, 3600));
        }
        server.takeRequest();
        assertEquals(EnergyOrderStatus.REFUSED, o.status());
        assertTrue(o.settled());
        assertFalse(o.needsAttention());
        assertEquals("SUPPLIER_REFUSED", o.errorCode());
        assertEquals("no supplier could take this order; nothing was bought and nothing was charged",
                o.error());
        // nobody was charged: nulls, not zeroes
        assertNull(o.priceUsd());
        assertNull(o.credits());
        assertNull(o.trxUsd());
        assertNull(o.deliveredEnergy());
        assertNull(o.deliveredAt());
    }

    @Test
    void anUnresolvedOrderComesBackFromA409NeedingAttention() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("""
                {"id": 125, "idempotency_key": "rent-3", "status": "unresolved",
                 "receive_address": "TSender", "energy": 65000, "duration_sec": 3600,
                 "price_sun": 3900000, "price_trx": "3.900000",
                 "settled": false, "needs_attention": true,
                 "error_code": "SUPPLIER_UNKNOWN",
                 "error": "the order's outcome never came back and it will not be retried - it may already have been bought; ask us to check it",
                 "created_at": "2026-09-18T10:00:00Z"}
                """));
        // 409 + needs_attention: do NOT retry; follow the order with order().
        EnergyOrder o = client.withIdempotencyKey("rent-3").energy().rent(
                EnergyRentRequest.of("TSender", 65000, 3600));
        server.takeRequest();
        assertEquals(EnergyOrderStatus.UNRESOLVED, o.status());
        assertFalse(o.settled());
        assertTrue(o.needsAttention());
        assertEquals("SUPPLIER_UNKNOWN", o.errorCode());
    }

    @Test
    void aRefusalForInsufficientCreditsComesBackFromA402AsTheOrder() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(402).setBody("""
                {"id": 126, "idempotency_key": "rent-4", "status": "refused",
                 "receive_address": "TSender", "energy": 65000, "duration_sec": 3600,
                 "price_sun": 3900000, "price_trx": "3.900000",
                 "settled": true, "needs_attention": false,
                 "error_code": "INSUFFICIENT_CREDITS",
                 "error": "your credit balance did not cover this order; nothing was bought and nothing was charged",
                 "created_at": "2026-09-18T10:00:00Z"}
                """));
        EnergyOrder o = client.withIdempotencyKey("rent-4").energy().rent(
                EnergyRentRequest.of("TSender", 65000, 3600));
        server.takeRequest();
        assertEquals(EnergyOrderStatus.REFUSED, o.status());
        assertEquals("INSUFFICIENT_CREDITS", o.errorCode());
        assertNull(o.credits());
    }

    @Test
    void anErrorEnvelopeThrowsWithTheEnvelopeCode() {
        server.enqueue(new MockResponse().setResponseCode(409).setBody(
                "{\"ok\":false,\"error\":\"NOT_WORTH_RENTING\","
                        + "\"msg\":\"renting is not cheaper than burning today\"}"));
        ApiException e = assertThrows(ApiException.class, () -> client.withIdempotencyKey("rent-5")
                .energy().rent(EnergyRentRequest.of("TSender", 65000, 3600)));
        assertEquals("NOT_WORTH_RENTING", e.code());
        assertEquals(409, e.status());
        assertEquals("renting is not cheaper than burning today", e.description());
    }

    @Test
    void orderLooksUpByTheIdempotencyKey() throws Exception {
        server.enqueue(new MockResponse().setBody(deliveredOrderJson()));
        EnergyOrder o = client.energy().order("rent-1");
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/energy/order", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertEquals("{\"key\":\"rent-1\"}", body);
        assertSigned(recorded, body, null);
        assertEquals("rent-1", o.idempotencyKey());
        assertEquals(EnergyOrderStatus.DELIVERED, o.status());
    }

    @Test
    void orderRejectsAnEmptyKeyBeforeAnyRequestGoesOut() {
        assertThrows(IllegalArgumentException.class, () -> client.energy().order(""));
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
