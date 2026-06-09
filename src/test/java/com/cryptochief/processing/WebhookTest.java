package com.cryptochief.processing;

import com.cryptochief.processing.http.CanonicalJson;
import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.webhook.PayoutWebhookEvent;
import com.cryptochief.processing.webhook.WebhookSignatureException;
import com.cryptochief.processing.webhook.WebhookVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTest {

    private static final String API_KEY = "secret";

    @Test
    void acceptsCanonicalBody() throws Exception {
        String body = "{\"event\":\"payout.paid\",\"status\":\"paid\",\"uuid\":\"u\",\"order_id\":\"o\"}";
        byte[] canonical = CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body));
        String sig = RequestSigner.sign(canonical, API_KEY);
        assertTrue(WebhookVerifier.verify(API_KEY, body.getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void acceptsReorderedBody() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("a", 1);
        ordered.put("b", 2);
        byte[] canonical = CanonicalJson.encode(ordered);
        String sig = RequestSigner.sign(canonical, API_KEY);
        String reordered = "{\"b\":2,\"a\":1}";
        assertTrue(WebhookVerifier.verify(API_KEY, reordered.getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void rejectsMutatedBody() throws Exception {
        String body = "{\"event\":\"payout.paid\",\"uuid\":\"u\",\"order_id\":\"o\",\"status\":\"paid\"}";
        byte[] canonical = CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body));
        String sig = RequestSigner.sign(canonical, API_KEY);
        String tampered = body.replace("\"paid\"", "\"failed\"");
        assertFalse(WebhookVerifier.verify(API_KEY, tampered.getBytes(StandardCharsets.UTF_8), sig));
    }

    @Test
    void parseReturnsTypedEvent() throws Exception {
        String body = "{\"event\":\"payout.paid\",\"uuid\":\"u-1\",\"order_id\":\"o-1\",\"status\":\"paid\"}";
        byte[] canonical = CanonicalJson.encode(CanonicalJson.MAPPER.readTree(body));
        String sig = RequestSigner.sign(canonical, API_KEY);
        PayoutWebhookEvent event = WebhookVerifier.parse(
                API_KEY, body.getBytes(StandardCharsets.UTF_8), sig, PayoutWebhookEvent.class);
        assertEquals("u-1", event.uuid());
        assertEquals("paid", event.status());
    }

    @Test
    void parseThrowsOnBadSignature() {
        assertThrows(WebhookSignatureException.class, () -> WebhookVerifier.parse(
                API_KEY,
                "{\"event\":\"x\",\"uuid\":\"u\",\"order_id\":\"o\",\"status\":\"paid\"}"
                        .getBytes(StandardCharsets.UTF_8),
                "bad",
                PayoutWebhookEvent.class));
    }
}
