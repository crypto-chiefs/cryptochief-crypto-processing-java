package com.cryptochief.processing;

import com.cryptochief.processing.http.CanonicalJson;
import com.cryptochief.processing.http.RequestSigner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SigningTest {

    private static final String API_KEY = "test_api_key_4242";

    @Test
    void emptyBodySignsAsMd5OfApiKey() throws Exception {
        String sig = RequestSigner.sign(new byte[0], API_KEY);
        assertEquals(expected("", API_KEY), sig);
    }

    @Test
    void canonicalSigningMatchesReferenceAlgorithm() throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("uuid", "abcd-1234");
        payload.put("network", "ETH_MAINNET");
        byte[] canonical = CanonicalJson.encode(payload);
        assertEquals("{\"network\":\"ETH_MAINNET\",\"uuid\":\"abcd-1234\"}",
                new String(canonical, StandardCharsets.UTF_8));
        String sig = RequestSigner.sign(canonical, API_KEY);
        assertEquals(expected(new String(canonical, StandardCharsets.UTF_8), API_KEY), sig);
    }

    @Test
    void signingParityVectorLocksAlgorithm() throws Exception {
        byte[] canonical = "{\"a\":1,\"b\":[2,3]}".getBytes(StandardCharsets.UTF_8);
        String sig = RequestSigner.sign(canonical, "my-secret-key");
        String b64 = Base64.getEncoder().encodeToString(canonical);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update((b64 + "my-secret-key").getBytes(StandardCharsets.UTF_8));
        byte[] digest = md5.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 0xFF));
        assertEquals(hex.toString(), sig);
    }

    @Test
    void rejectsEmptyApiKey() {
        assertThrows(IllegalArgumentException.class,
                () -> RequestSigner.sign("test".getBytes(StandardCharsets.UTF_8), ""));
    }

    private static String expected(String canonical, String key) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(canonical.getBytes(StandardCharsets.UTF_8));
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update((b64 + key).getBytes(StandardCharsets.UTF_8));
        byte[] digest = md5.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b & 0xFF));
        return hex.toString();
    }
}
