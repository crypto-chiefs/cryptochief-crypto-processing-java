package com.cryptochief.processing;

import com.cryptochief.processing.http.RequestSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HMAC v1 against the gateway's vectors ({@code hmac_v1_vectors.json}, copied unchanged).
 *
 * <p>The SDK signs requests and does not verify them, so every record is run through the signing side -
 * including the ones whose {@code expect} is a refusal, where the refusal is a header the receiver sees and
 * the record's own {@code string_to_sign} and {@code signature} are still the right answer for its fields.
 */
class HmacV1SigningTest {

    /** sha256 of the gateway's file; the copy here is byte for byte that file. */
    private static final String VECTORS_SHA256 =
            "a87df4921399dc14c7ceaa7e4c0dfa02495ad0400a5e722adfc0d3e3c1e064fe";

    private static byte[] vectorsFile() throws Exception {
        try (InputStream in = HmacV1SigningTest.class.getResourceAsStream("/hmac_v1_vectors.json")) {
            assertNotNull(in, "hmac_v1_vectors.json");
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

    @Test
    void recordCounts() throws Exception {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonNode v : vectors()) {
            counts.merge(v.get("expect").asText(), 1, Integer::sum);
        }
        assertEquals(Map.of("ok", 21, "bad_auth_headers", 24, "invalid_signature", 3,
                "timestamp_out_of_range", 2), counts);
        assertEquals(50, vectors().size());
    }

    @TestFactory
    Stream<DynamicTest> stringToSignAndSignature() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : vectors()) {
            String name = v.get("name").asText();
            tests.add(DynamicTest.dynamicTest(name, () -> {
                byte[] body = v.get("body").asText().getBytes(StandardCharsets.UTF_8);
                assertEquals(v.get("body_sha256").asText(), RequestSigner.bodySha256(body));
                assertEquals(v.get("string_to_sign").asText(), RequestSigner.hmacV1StringToSign(
                        v.get("timestamp").asText(), v.get("nonce").asText(), v.get("method").asText(),
                        v.get("path").asText(), v.get("query").asText(), v.get("merchant").asText(),
                        v.get("idempotency_key").asText(), body));
                assertEquals(v.get("signature").asText(), RequestSigner.signHmacV1(
                        v.get("api_key").asText(),
                        v.get("timestamp").asText(), v.get("nonce").asText(), v.get("method").asText(),
                        v.get("path").asText(), v.get("query").asText(), v.get("merchant").asText(),
                        v.get("idempotency_key").asText(), body));
            }));
        }
        return tests.stream();
    }

    @Test
    void nullsAreEmptyStrings() throws Exception {
        JsonNode v = vectors().get(0);
        assertEquals("", v.get("query").asText());
        assertEquals("", v.get("idempotency_key").asText());
        assertEquals("", v.get("body").asText());
        assertEquals(v.get("signature").asText(), RequestSigner.signHmacV1(
                v.get("api_key").asText(), v.get("timestamp").asText(), v.get("nonce").asText(),
                v.get("method").asText().toLowerCase(Locale.ROOT), v.get("path").asText(), null,
                v.get("merchant").asText(), null, null));
    }

    /** Line 4 as the gateway's ASCII upper-casing writes it: {@code a-z} only, every other byte as it is. */
    @Test
    void methodUpperCasesAsciiLettersOnly() {
        for (String[] c : new String[][] {{"post", "POST"}, {"pOsT", "POST"}, {"m-1_x.y~z", "M-1_X.Y~Z"},
                {"PUT", "PUT"}, {"", ""}, {"POST ", "POST "},
                // Full Unicode case mapping would write SS, FI, ʼN, É; the servers do not.
                {"ß", "ß"}, {"ﬁx", "ﬁX"}, {"ŉ", "ŉ"}, {"é", "é"},
                {"пост", "пост"},
                {"gétß", "GéTß"}}) {
            String sts = RequestSigner.hmacV1StringToSign("871824817281", "SC8fg_HhhQ5aPBw4ygU", c[0], "/v1/x",
                    "", "m", "", null);
            assertEquals(c[1], sts.split("\n", -1)[3], c[0]);
            assertEquals(c[1], RequestSigner.upperAsciiMethod(c[0]), c[0]);
        }
    }

    /** A non-ASCII method is signed, not rejected: its bytes go into the string to sign unchanged. */
    @Test
    void nonAsciiMethodIsSignedAsGiven() {
        String sts = RequestSigner.hmacV1StringToSign("1789430400", "0123456789abcdef0123456789abcdef",
                "gét", "/v1/x", "", "m", "", null);
        assertEquals("GéT", sts.split("\n", -1)[3]);
        assertEquals(64, RequestSigner.signHmacV1("key", "1789430400", "0123456789abcdef0123456789abcdef",
                "gét", "/v1/x", "", "m", "", null).length());
    }

    @Test
    void rejectsLineBreaksInFields() {
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.hmacV1StringToSign(
                "1789430400", "0123456789abcdef0123456789abcdef", "POST", "/v1/x",
                "", "merchant", "key\nX", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> RequestSigner.hmacV1StringToSign(
                "1789430400", "0123456789abcdef0123456789abcdef", "POST", "/v1/x",
                "a=1\r", "merchant", "", new byte[0]));
    }

    /** Empty, spaces or tabs: no key at all, and signing with one is an error rather than a signature. */
    @Test
    void rejectsBlankApiKey() {
        for (String key : new String[] {null, "", " ", "\t", " \t  \t"}) {
            assertTrue(RequestSigner.isBlankKey(key), String.valueOf(key));
            assertThrows(IllegalArgumentException.class, () -> RequestSigner.signHmacV1(
                    key, "1789430400", "0123456789abcdef0123456789abcdef", "POST", "/v1/x",
                    "", "merchant", "", new byte[0]), String.valueOf(key));
            assertThrows(IllegalArgumentException.class, () -> RequestSigner.signWebhookV1(
                    key, 1789430400L, "dlv_1", new byte[0]), String.valueOf(key));
        }
        // Options keeps its null contract; a blank key is refused the same way an empty one is.
        for (String key : new String[] {"", " ", "\t", " \t  \t"}) {
            assertThrows(IllegalArgumentException.class, () -> Options.builder()
                    .merchantId("m").apiKey(key).build(), "\"" + key + "\"");
        }
        for (String key : new String[] {"k", " k ", "\n", " "}) {
            assertFalse(RequestSigner.isBlankKey(key), key);
        }
    }

    /** The signed path is the percent-decoded route; the escaped spelling is what goes on the wire. */
    @Test
    void pathIsSignedPercentDecoded() {
        assertEquals("/v1/orders/payout/8814", RequestSigner.decodePath("/v1/orders/payout%2F8814"));
        assertEquals("/v1/orders/payout/8814", RequestSigner.decodePath("/v1/orders/payout%2f8814"));
        assertEquals("/v1/a b", RequestSigner.decodePath("/v1/a%20b"));
        assertEquals("/v1/заказ №1",
                RequestSigner.decodePath("/v1/%D0%B7%D0%B0%D0%BA%D0%B0%D0%B7%20%E2%84%961"));
        assertEquals("/v1/x", RequestSigner.decodePath("/v1/x"));
        assertEquals("/v1/заказ", RequestSigner.decodePath("/v1/заказ"));
        assertEquals("", RequestSigner.decodePath(null));
        assertEquals("/v1/a+b", RequestSigner.decodePath("/v1/a+b"));

        for (String bad : new String[] {"/v1/100%", "/v1/%2", "/v1/%zz", "/v1/%2z"}) {
            assertThrows(IllegalArgumentException.class, () -> RequestSigner.decodePath(bad), bad);
        }

        String sts = RequestSigner.hmacV1StringToSign("1789430400", "0123456789abcdef0123456789abcdef", "POST",
                RequestSigner.decodePath("/v1/orders/payout%2F8814"), "ref=a%2Fb&q=%D1%82", "m", "", null);
        String[] lines = sts.split("\n", -1);
        assertEquals("/v1/orders/payout/8814", lines[4]);
        assertEquals("ref=a%2Fb&q=%D1%82", lines[5]);
    }

    /** The request signer returns the bare hex, the webhook signer the whole header value. */
    @Test
    void requestSignatureHasNoPrefixWebhookSignatureHasOne() throws Exception {
        JsonNode v = vectors().get(0);
        String request = RequestSigner.signHmacV1(v.get("api_key").asText(), v.get("timestamp").asText(),
                v.get("nonce").asText(), v.get("method").asText(), v.get("path").asText(),
                v.get("query").asText(), v.get("merchant").asText(), v.get("idempotency_key").asText(),
                v.get("body").asText().getBytes(StandardCharsets.UTF_8));
        String webhook = RequestSigner.signWebhookV1("key", 1789430400L, "dlv_1",
                "{}".getBytes(StandardCharsets.UTF_8));

        assertTrue(request.matches("[0-9a-f]{64}"), request);
        assertEquals("v1=", RequestSigner.HMAC_V1_PREFIX);
        assertTrue(webhook.startsWith(RequestSigner.HMAC_V1_PREFIX), webhook);
        assertTrue(webhook.substring(RequestSigner.HMAC_V1_PREFIX.length()).matches("[0-9a-f]{64}"), webhook);
    }

    @Test
    void nonceIs32LowerHexAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String nonce = RequestSigner.newNonce();
            assertTrue(nonce.matches("[0-9a-f]{32}"), nonce);
            assertTrue(seen.add(nonce), nonce);
        }
    }
}
