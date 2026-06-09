package com.cryptochief.processing.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** {@code signature = hex(md5(base64(canonicalJson(body)) + apiKey))}. */
public final class RequestSigner {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RequestSigner() {}

    public static String sign(byte[] canonicalBody, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key is required");
        }
        String b64 = Base64.getEncoder().encodeToString(canonicalBody);
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
        md5.update(b64.getBytes(StandardCharsets.UTF_8));
        md5.update(apiKey.getBytes(StandardCharsets.UTF_8));
        return toHexLower(md5.digest());
    }

    private static String toHexLower(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
