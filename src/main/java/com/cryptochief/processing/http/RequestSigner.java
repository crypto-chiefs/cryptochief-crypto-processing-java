package com.cryptochief.processing.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 v1 signatures.
 *
 * <ul>
 *   <li>Request ({@code X-CC-Signature}): {@code "v1=" + hex(hmacSha256(apiKey, stringToSign))}, see
 *       {@link #hmacV1StringToSign}.</li>
 *   <li>Webhook ({@code X-CC-Signature}): {@link #signWebhookV1}, see {@link #webhookV1StringToSign}; verified by
 *       {@link com.cryptochief.processing.webhook.WebhookVerifier}.</li>
 * </ul>
 */
public final class RequestSigner {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** First line of the HMAC v1 string to sign. */
    public static final String HMAC_V1_SCOPE = "CC-HMAC-SHA256-REQ-V1";
    /** Prefix of the {@code X-CC-Signature} value. */
    public static final String HMAC_V1_PREFIX = "v1=";

    public static final String HEADER_TIMESTAMP = "X-CC-Timestamp";
    public static final String HEADER_NONCE = "X-CC-Nonce";
    public static final String HEADER_HMAC_SIGNATURE = "X-CC-Signature";
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    /** First line of the webhook string to sign. */
    public static final String WEBHOOK_V1_SCOPE = "CC-HMAC-SHA256-WEBHOOK-V1";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RequestSigner() {}

    /**
     * HMAC v1 string to sign:
     *
     * <pre>
     * CC-HMAC-SHA256-REQ-V1\n
     * timestamp\n
     * nonce\n
     * METHOD\n
     * path\n
     * query\n
     * merchant\n
     * idempotencyKey\n
     * hex(sha256(body))
     * </pre>
     *
     * {@code null} query, idempotency key and body are empty. {@code METHOD}: {@code a-z} upper-cased, every
     * other byte unchanged. {@code path} is the route percent-decoded, the form the server reads
     * ({@link #decodePath}); {@code query} is the raw string the URL carries.
     *
     * @throws IllegalArgumentException a value contains CR or LF
     */
    public static String hmacV1StringToSign(String timestamp, String nonce, String method, String path,
                                            String query, String merchant, String idempotencyKey,
                                            byte[] body) {
        String[] fields = {
                orEmpty(timestamp),
                orEmpty(nonce),
                upperAsciiMethod(orEmpty(method)),
                orEmpty(path),
                orEmpty(query),
                orEmpty(merchant),
                orEmpty(idempotencyKey),
        };
        StringBuilder sb = new StringBuilder(HMAC_V1_SCOPE);
        for (String field : fields) {
            if (field.indexOf('\r') >= 0 || field.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("HMAC v1 field contains CR or LF");
            }
            sb.append('\n').append(field);
        }
        sb.append('\n').append(bodySha256(body));
        return sb.toString();
    }

    /**
     * Lowercase hex HMAC-SHA256 of {@link #hmacV1StringToSign} keyed with the UTF-8 bytes of {@code apiKey}.
     *
     * <p>The returned value is the hex alone: the {@code X-CC-Signature} header is
     * {@link #HMAC_V1_PREFIX} + this. {@link #signWebhookV1} returns the header value itself, prefix included.
     *
     * @throws IllegalArgumentException blank {@code apiKey}, or see {@link #hmacV1StringToSign}
     */
    public static String signHmacV1(String apiKey, String timestamp, String nonce, String method, String path,
                                    String query, String merchant, String idempotencyKey, byte[] body) {
        requireKey(apiKey);
        String stringToSign = hmacV1StringToSign(timestamp, nonce, method, path, query, merchant,
                idempotencyKey, body);
        return toHexLower(hmacSha256(apiKey, stringToSign));
    }

    /**
     * Webhook string to sign:
     *
     * <pre>
     * CC-HMAC-SHA256-WEBHOOK-V1\n
     * timestamp\n
     * deliveryId\n
     * hex(sha256(body))
     * </pre>
     *
     * @param timestamp  Unix seconds, {@code X-CC-Timestamp}
     * @param deliveryId {@code X-Webhook-Delivery}: 1-128 characters {@code [A-Za-z0-9_-]}
     * @param body       the exact body bytes; {@code null} is the empty body
     * @throws IllegalArgumentException {@code timestamp} is not positive or {@code deliveryId} is malformed
     */
    public static String webhookV1StringToSign(long timestamp, String deliveryId, byte[] body) {
        if (timestamp <= 0) {
            throw new IllegalArgumentException("webhook timestamp must be positive");
        }
        if (!isDeliveryId(deliveryId)) {
            throw new IllegalArgumentException("webhook delivery id must be 1-128 characters [A-Za-z0-9_-]");
        }
        return WEBHOOK_V1_SCOPE + '\n' + timestamp + '\n' + deliveryId + '\n' + bodySha256(body);
    }

    /**
     * {@code X-CC-Signature} value of a webhook: {@code "v1=" + lowercase hex HMAC-SHA256} of
     * {@link #webhookV1StringToSign} keyed with the UTF-8 bytes of {@code apiKey}.
     *
     * @throws IllegalArgumentException blank {@code apiKey}, or see {@link #webhookV1StringToSign}
     */
    public static String signWebhookV1(String apiKey, long timestamp, String deliveryId, byte[] body) {
        requireKey(apiKey);
        return HMAC_V1_PREFIX + toHexLower(hmacSha256(apiKey, webhookV1StringToSign(timestamp, deliveryId, body)));
    }

    /** Lowercase hex SHA-256 of {@code body}; {@code null} is the empty body. */
    public static String bodySha256(byte[] body) {
        try {
            return toHexLower(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 32 lowercase hex characters from 16 random bytes. */
    public static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return toHexLower(bytes);
    }

    /** {@code true}: 1-128 characters {@code [A-Za-z0-9_-]}. */
    private static boolean isDeliveryId(String s) {
        if (s == null || s.isEmpty() || s.length() > 128) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    /**
     * {@code true} when {@code apiKey} signs nothing: {@code null}, empty, or spaces and tabs only. The server
     * trims spaces and tabs off a header before it reads it and refuses a project whose key is blank, so a key
     * of {@code " "} is no key at all rather than a one-character secret.
     */
    public static boolean isBlankKey(String apiKey) {
        if (apiKey == null) return true;
        for (int i = 0; i < apiKey.length(); i++) {
            char c = apiKey.charAt(i);
            if (c != ' ' && c != '\t') return false;
        }
        return true;
    }

    /**
     * The route as the server reads it: {@code %XX} decoded as UTF-8, everything else unchanged.
     * {@code /v1/orders/payout%2F8814} decodes to {@code /v1/orders/payout/8814}, which is what gets signed
     * while the escaped spelling goes on the wire.
     *
     * @throws IllegalArgumentException {@code path} holds a {@code %} that is not followed by two hex digits
     */
    public static String decodePath(String path) {
        if (path == null) return "";
        int percent = path.indexOf('%');
        if (percent < 0) return path;

        StringBuilder out = new StringBuilder(path.length());
        out.append(path, 0, percent);
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        for (int i = percent; i < path.length(); ) {
            char c = path.charAt(i);
            if (c != '%') {
                flush(octets, out);
                out.append(c);
                i++;
                continue;
            }
            if (i + 2 >= path.length()) {
                throw new IllegalArgumentException("invalid percent-escape in path: " + path);
            }
            int hi = hexDigit(path.charAt(i + 1));
            int lo = hexDigit(path.charAt(i + 2));
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid percent-escape in path: " + path);
            }
            octets.write((hi << 4) | lo);
            i += 3;
        }
        flush(octets, out);
        return out.toString();
    }

    /** Decodes the pending percent-escaped octets as one UTF-8 run. */
    private static void flush(ByteArrayOutputStream octets, StringBuilder out) {
        if (octets.size() == 0) return;
        out.append(new String(octets.toByteArray(), StandardCharsets.UTF_8));
        octets.reset();
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static void requireKey(String apiKey) {
        if (isBlankKey(apiKey)) {
            throw new IllegalArgumentException("API key is required");
        }
    }

    private static byte[] hmacSha256(String apiKey, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * The HTTP method as line 4 of the string to sign carries it: {@code a-z} upper-cased, every other byte
     * left alone - what the servers' ASCII upper-casing does to a method token.
     *
     * <p>Not {@link String#toUpperCase()}: that applies the full Unicode mapping, turning {@code ß} into
     * {@code SS} and {@code ﬁ} into {@code FI}, and a signature built over that string matches nothing the
     * server computes.
     */
    public static String upperAsciiMethod(String method) {
        if (method == null) return "";
        char[] out = method.toCharArray();
        for (int i = 0; i < out.length; i++) {
            char c = out[i];
            if (c >= 'a' && c <= 'z') {
                out[i] = (char) (c - ('a' - 'A'));
            }
        }
        return new String(out);
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
