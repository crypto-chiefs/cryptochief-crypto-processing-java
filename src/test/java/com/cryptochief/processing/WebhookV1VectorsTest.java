package com.cryptochief.processing;

import com.cryptochief.processing.http.RequestSigner;
import com.cryptochief.processing.webhook.WebhookHeadersException;
import com.cryptochief.processing.webhook.WebhookOptions;
import com.cryptochief.processing.webhook.WebhookSignatureException;
import com.cryptochief.processing.webhook.WebhookTimestampException;
import com.cryptochief.processing.webhook.WebhookVerificationException;
import com.cryptochief.processing.webhook.WebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Webhook HMAC v1 against the webhook service's vectors ({@code webhook_hmac_v1_vectors.json}, copied unchanged),
 * through the public functions only.
 */
class WebhookV1VectorsTest {

    /** sha256 of the webhook service's file; the copy here is byte for byte that file. */
    private static final String VECTORS_SHA256 =
            "15a6e1423708e8c3b9ec4fac7ee6eb383db56703605647e02308a29166722502";

    private static byte[] vectorsFile() throws Exception {
        try (InputStream in = WebhookV1VectorsTest.class.getResourceAsStream("/webhook_hmac_v1_vectors.json")) {
            assertNotNull(in, "webhook_hmac_v1_vectors.json");
            return in.readAllBytes();
        }
    }

    private static JsonNode vectors() throws Exception {
        return new ObjectMapper().readTree(vectorsFile());
    }

    @Test
    void vectorsFileIsTheReferenceCopy() throws Exception {
        assertEquals(VECTORS_SHA256, RequestSigner.bodySha256(vectorsFile()));
    }

    private static byte[] body(JsonNode v) {
        if (v.has("body_base64")) {
            return Base64.getDecoder().decode(v.get("body_base64").asText());
        }
        return v.get("body").asText().getBytes(StandardCharsets.UTF_8);
    }

    /** Header name to values as the receiver sees them: record defaults, then the record's overrides. */
    private static Map<String, List<String>> headers(JsonNode v) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(WebhookVerifier.TIMESTAMP_HEADER, List.of(v.get("timestamp").asText()));
        headers.put(WebhookVerifier.DELIVERY_HEADER, List.of(v.get("delivery_id").asText()));
        headers.put(WebhookVerifier.SIGNATURE_HEADER, List.of(v.get("signature").asText()));
        JsonNode overrides = v.get("headers");
        if (overrides != null) {
            for (Map.Entry<String, JsonNode> e : overrides.properties()) {
                List<String> values = new ArrayList<>();
                e.getValue().forEach(item -> values.add(item.asText()));
                headers.put(e.getKey(), values);
            }
        }
        return headers;
    }

    private static WebhookOptions options(JsonNode v) {
        return WebhookOptions.defaults()
                .withTolerance(Duration.ofSeconds(300))
                .withClock(Clock.fixed(Instant.ofEpochSecond(v.get("now").asLong()), ZoneOffset.UTC));
    }

    private static Class<? extends WebhookVerificationException> refusal(String expect) {
        return switch (expect) {
            case "bad_headers" -> WebhookHeadersException.class;
            case "timestamp_out_of_range" -> WebhookTimestampException.class;
            case "bad_signature" -> WebhookSignatureException.class;
            default -> throw new IllegalArgumentException("unknown expect: " + expect);
        };
    }

    private static void assertOutcome(String expect, Executable call, String name) throws Throwable {
        if ("ok".equals(expect)) {
            call.execute();
        } else {
            assertEquals(refusal(expect), assertThrows(WebhookVerificationException.class, call, name).getClass(),
                    name);
        }
    }

    @Test
    void recordCounts() throws Exception {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonNode v : vectors()) {
            counts.merge(v.get("expect").asText(), 1, Integer::sum);
        }
        assertEquals(Map.of("ok", 22, "bad_headers", 26, "bad_signature", 4, "timestamp_out_of_range", 2), counts);
    }

    @TestFactory
    Stream<DynamicTest> stringToSignAndSignature() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : vectors()) {
            String name = v.get("name").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> {
                byte[] body = body(v);
                long timestamp = v.get("timestamp").asLong();
                String deliveryId = v.get("delivery_id").asText();
                assertEquals(v.get("body_sha256").asText(), RequestSigner.bodySha256(body));
                assertEquals(v.get("string_to_sign").asText(),
                        RequestSigner.webhookV1StringToSign(timestamp, deliveryId, body));
                assertEquals(v.get("signature").asText(),
                        RequestSigner.signWebhookV1(v.get("api_key").asText(), timestamp, deliveryId, body));
            }));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> verifyWithHeaderMap() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : vectors()) {
            String name = v.get("name").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> assertOutcome(v.get("expect").asText(),
                    () -> WebhookVerifier.verify(v.get("api_key").asText(), body(v), headers(v), options(v)),
                    name)));
        }
        return tests.stream();
    }

    /** Records without repeated headers, through a single-value lookup. */
    @TestFactory
    Stream<DynamicTest> verifyWithHeaderLookup() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : vectors()) {
            Map<String, List<String>> headers = headers(v);
            if (headers.values().stream().anyMatch(values -> values.size() > 1)) continue;
            Map<String, String> lookup = new HashMap<>();
            headers.forEach((k, values) -> {
                if (!values.isEmpty()) lookup.put(k, values.get(0));
            });
            String name = v.get("name").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> assertOutcome(v.get("expect").asText(),
                    () -> WebhookVerifier.verify(v.get("api_key").asText(), body(v), lookup::get, options(v)),
                    name)));
        }
        assertEquals(51, tests.size());
        return tests.stream();
    }
}
