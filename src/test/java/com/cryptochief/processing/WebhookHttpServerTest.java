package com.cryptochief.processing;

import com.cryptochief.processing.exceptions.DecodeException;
import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.WebhookVerificationException;
import com.cryptochief.processing.webhook.WebhookVerifier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Webhook handlers on a JDK HTTP server: real request bytes and headers as the server hands them over. */
class WebhookHttpServerTest {

    private static final String API_KEY = "whk_test_key";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String BODY = "{\"event\":\"payout.paid\",\"uuid\":\"u-9\",\"order_id\":\"payout?batch=7&row=3\","
            + "\"status\":\"paid\",\"note\":\"Кофе & <b>\"}";

    private HttpServer server;
    private OkHttpClient http;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // Header map overload.
        server.createContext("/webhook/map", exchange -> handle(exchange, true));
        // Single-value lookup overload.
        server.createContext("/webhook/lookup", exchange -> handle(exchange, false));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new OkHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    private static void handle(HttpExchange exchange, boolean headerMap) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        int status;
        String answer;
        try {
            PayoutWebhookEvent event = headerMap
                    ? WebhookVerifier.parse(API_KEY, body, exchange.getRequestHeaders(), PayoutWebhookEvent.class)
                    : WebhookVerifier.parse(API_KEY, body, exchange.getRequestHeaders()::getFirst,
                            PayoutWebhookEvent.class);
            status = 200;
            answer = event.uuid() + " " + event.orderId();
        } catch (WebhookVerificationException e) {
            status = 401;
            answer = e.getClass().getSimpleName();
        } catch (DecodeException e) {
            status = 400;
            answer = "decode";
        }
        byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Request.Builder webhook(String url, byte[] body, long timestamp, String deliveryId) {
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .header(WebhookVerifier.DELIVERY_HEADER, deliveryId)
                .header(WebhookVerifier.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(WebhookVerifier.SIGNATURE_HEADER,
                        RequestSigner.signWebhookV1(API_KEY, timestamp, deliveryId, body));
    }

    private String call(Request request) throws IOException {
        try (Response response = http.newCall(request).execute()) {
            return response.code() + " " + response.body().string();
        }
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    @Test
    void acceptsSignedDelivery() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String delivery = UUID.randomUUID().toString();
        assertEquals("200 u-9 payout?batch=7&row=3", call(webhook(baseUrl + "/webhook/map", body, now(), delivery)
                .build()));
        assertEquals("200 u-9 payout?batch=7&row=3", call(webhook(baseUrl + "/webhook/lookup", body, now(), delivery)
                .build()));
    }

    @Test
    void acceptsLowerCaseHeaderNamesAndUpperCaseHex() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        long ts = now();
        String delivery = UUID.randomUUID().toString();
        String hex = RequestSigner.signWebhookV1(API_KEY, ts, delivery, body).substring(3).toUpperCase(Locale.ROOT);
        Request request = new Request.Builder()
                .url(baseUrl + "/webhook/map")
                .post(RequestBody.create(body, JSON))
                .header("x-webhook-delivery", delivery)
                .header("x-cc-timestamp", Long.toString(ts))
                .header("x-cc-signature", "v1=" + hex)
                .build();
        assertEquals("200 u-9 payout?batch=7&row=3", call(request));
        assertEquals("200 u-9 payout?batch=7&row=3", call(request.newBuilder().url(baseUrl + "/webhook/lookup").build()));
    }

    @Test
    void refusesChangedBody() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        Request signed = webhook(baseUrl + "/webhook/map", body, now(), "dlv_1").build();
        Request tampered = signed.newBuilder()
                .post(RequestBody.create(BODY.replace("paid", "fail").getBytes(StandardCharsets.UTF_8), JSON))
                .build();
        assertEquals("401 WebhookSignatureException", call(tampered));
    }

    @Test
    void refusesRepeatedHeaderOverTheWire() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        long ts = now();
        Request repeated = webhook(baseUrl + "/webhook/map", body, ts, "dlv_2")
                .addHeader("x-cc-signature", RequestSigner.signWebhookV1(API_KEY, ts, "dlv_2", body))
                .build();
        assertEquals("401 WebhookHeadersException", call(repeated));
    }

    /** The signature still matches the canonical number, so only the header format refuses this. */
    @Test
    void refusesTimestampRewrittenWithALeadingZero() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        long ts = now();
        Request padded = webhook(baseUrl + "/webhook/map", body, ts, "dlv_6")
                .header(WebhookVerifier.TIMESTAMP_HEADER, "0" + ts)
                .build();
        assertEquals("401 WebhookHeadersException", call(padded));
        assertEquals("401 WebhookHeadersException", call(padded.newBuilder().url(baseUrl + "/webhook/lookup").build()));
    }

    @Test
    void refusesStaleTimestamp() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        assertEquals("401 WebhookTimestampException",
                call(webhook(baseUrl + "/webhook/lookup", body, now() - 301 - 5, "dlv_3").build()));
    }

    @Test
    void refusesDeliveryWithoutHmacHeaders() throws IOException {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        Request legacy = new Request.Builder()
                .url(baseUrl + "/webhook/map")
                .post(RequestBody.create(body, JSON))
                .header("Signature", "0123456789abcdef0123456789abcdef")
                .header("X-Webhook-Signature", "0123456789abcdef0123456789abcdef")
                .header(WebhookVerifier.DELIVERY_HEADER, "dlv_4")
                .build();
        assertEquals("401 WebhookHeadersException", call(legacy));
        assertEquals("401 WebhookHeadersException", call(legacy.newBuilder().url(baseUrl + "/webhook/lookup").build()));
    }

    @Test
    void verifiedNonEventBodyIsADecodeError() throws IOException {
        byte[] body = "null".getBytes(StandardCharsets.UTF_8);
        assertEquals("400 decode", call(webhook(baseUrl + "/webhook/map", body, now(), "dlv_5").build()));
    }
}
