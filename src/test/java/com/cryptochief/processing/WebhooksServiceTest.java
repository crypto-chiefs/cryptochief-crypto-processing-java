package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.models.StaticDepositResendResult;
import com.cryptochief.processing.models.WebhookAttempt;
import com.cryptochief.processing.models.WebhookDelivery;
import com.cryptochief.processing.models.WebhookDeliveryStatus;
import com.cryptochief.processing.webhook.WebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbound-webhook surface: reading a delivery with its attempts, the three routes, and
 * that a refusal is an ApiException with the machine code rather than a queued=false result.
 */
class WebhooksServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DELIVERY_BODY = """
            {
              "uuid": "44444444-4444-4444-8444-444444444444",
              "event_type": "invoice.paid", "reference": "order-1", "target_url": "https://m.example/hook",
              "status": "failed", "attempts": 3, "max_attempts": 10, "resend_count": 1,
              "last_error": "HTTP 500", "last_http_status": 500, "next_attempt_at": null, "delivered_at": null,
              "created_at": "2026-09-03T10:00:00Z", "superseded_by": null,
              "attempt_history": [
                {"attempt": 3, "http_status": 500, "error": "HTTP 500", "duration_ms": 120, "target_url": "https://m.example/hook",
                 "created_at": "2026-09-03T10:02:00Z", "response_body": "<html>oops", "response_content_type": "text/html", "response_truncated": true},
                {"attempt": 2, "http_status": null, "error": "dial tcp: connection refused", "duration_ms": null, "target_url": "https://m.example/hook",
                 "created_at": null, "response_body": null, "response_content_type": null, "response_truncated": false}
              ],
              "payload": {"body": "{\\"event\\":\\"invoice.paid\\"}", "bytes": 24, "truncated": false}
            }
            """;

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
    void infoReadsAttemptsAndKeepsNullAsNotRecorded() throws Exception {
        server.enqueue(new MockResponse().setBody(DELIVERY_BODY).addHeader("Content-Type", "application/json"));

        WebhookDelivery d = client.webhooks().info("44444444-4444-4444-8444-444444444444");

        RecordedRequest req = server.takeRequest();
        assertEquals("/v1/webhooks/info", req.getPath());
        JsonNode body = MAPPER.readTree(req.getBody().readUtf8());
        assertEquals("44444444-4444-4444-8444-444444444444", body.get("uuid").asText());

        assertEquals(WebhookDeliveryStatus.FAILED, d.status());
        assertEquals(500, d.lastHttpStatus());
        assertNull(d.deliveredAt());
        assertNull(d.supersededBy());
        assertEquals(2, d.attemptHistory().size());
        WebhookAttempt answered = d.attemptHistory().get(0);
        WebhookAttempt silent = d.attemptHistory().get(1);
        assertTrue(answered.responseTruncated());
        assertEquals("text/html", answered.responseContentType());
        // An attempt nothing answered has no status and no body - only the error.
        assertNull(silent.httpStatus());
        assertNull(silent.responseBody());
        assertNull(silent.createdAt());
        assertTrue(silent.error().contains("connection refused"));
        assertEquals(24, d.payload().bytes());
    }

    @Test
    void resendStaticDepositIsAddressedByTheDepositUuid() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"uuid":"dep-1","deliveries":[{"uuid":"d-1","event_type":"static_deposit.paid","reference":"dep-1",
                 "status":"delivered","queued":true,"attempts":2,"resend_count":1}],"queued":1,"total":1}
                """).addHeader("Content-Type", "application/json"));

        StaticDepositResendResult out = client.webhooks().resendStaticDeposit("dep-1");

        RecordedRequest req = server.takeRequest();
        assertEquals("/v1/static-deposits/resend", req.getPath());
        assertEquals("dep-1", MAPPER.readTree(req.getBody().readUtf8()).get("uuid").asText());
        assertEquals(1, out.queued());
        assertTrue(out.deliveries().get(0).queued());
        assertEquals(1, out.deliveries().get(0).resendCount());
    }

    @Test
    void refusalIsAnApiExceptionWithTheCode() {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("""
                {"ok":false,"error":"DELIVERY_SUPERSEDED","msg":"not the latest; resend invoice.paid instead","superseded_by":"invoice.paid"}
                """).addHeader("Content-Type", "application/json"));

        ApiException e = assertThrows(ApiException.class,
                () -> client.webhooks().resend("44444444-4444-4444-8444-444444444444"));

        assertEquals(ErrorCode.DELIVERY_SUPERSEDED, e.code());
        assertEquals(409, e.status());
        assertTrue(e.getMessage().contains("invoice.paid"));
    }

    @Test
    void deliveryHeaderName() {
        assertEquals("X-Webhook-Delivery", WebhookVerifier.DELIVERY_HEADER);
    }
}
