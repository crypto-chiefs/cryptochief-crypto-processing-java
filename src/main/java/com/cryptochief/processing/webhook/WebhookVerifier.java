package com.cryptochief.processing.webhook;

import com.cryptochief.processing.exceptions.DecodeException;
import com.cryptochief.processing.http.CanonicalJson;
import com.cryptochief.processing.http.RequestSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Webhook signature verification. Algorithm matches request signing. */
public final class WebhookVerifier {

    public static final String HEADER = "Signature";

    public static final List<String> SENDER_IPS = List.of("164.90.231.203", "104.248.248.64");

    private WebhookVerifier() {}

    public static boolean verify(String apiKey, byte[] body, String signatureHeader) {
        if (apiKey == null || apiKey.isEmpty()) return false;
        if (body == null || body.length == 0) return false;
        if (signatureHeader == null || signatureHeader.isEmpty()) return false;
        byte[] canonical = canonicalise(body);
        if (canonical == null) return false;
        String expected = RequestSigner.sign(canonical, apiKey);
        return constantTimeEquals(expected, signatureHeader);
    }

    public static void requireValid(String apiKey, byte[] body, String signatureHeader) {
        if (!verify(apiKey, body, signatureHeader)) {
            throw new WebhookSignatureException("cryptochief: invalid webhook signature");
        }
    }

    /** Verify and decode the body into a typed event in one call. */
    public static <T> T parse(String apiKey, byte[] body, String signatureHeader, Class<T> eventType) {
        requireValid(apiKey, body, signatureHeader);
        try {
            return CanonicalJson.MAPPER.readValue(new String(body, StandardCharsets.UTF_8), eventType);
        } catch (JsonProcessingException e) {
            throw new DecodeException("cryptochief: webhook decode failed: " + e.getMessage(), e);
        }
    }

    private static byte[] canonicalise(byte[] body) {
        try {
            JsonNode tree = CanonicalJson.MAPPER.readTree(body);
            return CanonicalJson.encode(tree);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
