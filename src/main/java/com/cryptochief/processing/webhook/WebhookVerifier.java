package com.cryptochief.processing.webhook;

import com.cryptochief.processing.exceptions.DecodeException;
import com.cryptochief.processing.http.Json;
import com.cryptochief.processing.http.RequestSigner;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Webhook verification, HMAC-SHA256 v1.
 *
 * <pre>
 * CC-HMAC-SHA256-WEBHOOK-V1\n
 * X-CC-Timestamp\n
 * X-Webhook-Delivery\n
 * hex(sha256(raw body))
 * </pre>
 *
 * {@code X-CC-Signature = "v1=" + hex(hmacSha256(apiKey, stringToSign))}.
 *
 * <p>Checks, in order:
 * <ol>
 *   <li>each of {@link #TIMESTAMP_HEADER}, {@link #DELIVERY_HEADER}, {@link #SIGNATURE_HEADER} present once,
 *       names compared case-insensitively, spaces and tabs trimmed from the value; timestamp decimal digits
 *       without a leading zero, delivery id 1-128 characters {@code [A-Za-z0-9_-]}, signature {@code v1=} and
 *       64 hex digits of any case
 *       - else {@link WebhookHeadersException};</li>
 *   <li>{@code |now - X-CC-Timestamp| <= tolerance} - else {@link WebhookTimestampException};</li>
 *   <li>signature, compared in constant time - else {@link WebhookSignatureException}.</li>
 * </ol>
 *
 * The body is the raw request bytes, read before any JSON parsing. {@link RequestSigner#signWebhookV1} produces
 * the signature.
 */
public final class WebhookVerifier {

    /**
     * Delivery id. Constant across every attempt and resend of one delivery: the receiver's idempotency key
     * and the argument of {@code client.webhooks().info()} / {@code resend()}.
     */
    public static final String DELIVERY_HEADER = "X-Webhook-Delivery";

    /** Unix time of the attempt's signature, seconds. */
    public static final String TIMESTAMP_HEADER = "X-CC-Timestamp";

    /** {@code v1=<64 hex>}. */
    public static final String SIGNATURE_HEADER = "X-CC-Signature";

    /** Default maximum difference between {@code X-CC-Timestamp} and the current time. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);

    /** Source addresses of the processing platform's webhooks. */
    public static final List<String> SENDER_IPS = List.of("164.90.231.203", "104.248.248.64");

    private static final String PREFIX = RequestSigner.HMAC_V1_PREFIX;

    private WebhookVerifier() {}

    /**
     * Verifies with {@link WebhookOptions#defaults()}.
     *
     * @see #verify(String, byte[], Function, WebhookOptions)
     */
    public static void verify(String apiKey, byte[] rawBody, Function<String, String> header) {
        verify(apiKey, rawBody, header, WebhookOptions.defaults());
    }

    /**
     * Verifies a webhook through a header lookup.
     *
     * @param apiKey  the project's {@code api_key}; blank - empty or spaces and tabs only - is no key
     * @param rawBody the request body bytes as received
     * @param header  value of the named header or {@code null}, e.g. {@code request::getHeader}; a lookup
     *                that returns one of several repeated values hides the repetition, the {@code Map}
     *                overloads detect it
     * @throws IllegalArgumentException blank {@code apiKey}
     * @throws WebhookHeadersException    signature headers missing or malformed
     * @throws WebhookTimestampException  timestamp outside the tolerance
     * @throws WebhookSignatureException  signature mismatch
     */
    public static void verify(String apiKey, byte[] rawBody, Function<String, String> header,
                              WebhookOptions options) {
        Objects.requireNonNull(header, "header");
        check(apiKey, rawBody, options, name -> {
            String value = header.apply(name);
            return value == null ? List.of() : List.of(value);
        });
    }

    /**
     * Verifies with {@link WebhookOptions#defaults()}.
     *
     * @see #verify(String, byte[], Map, WebhookOptions)
     */
    public static void verify(String apiKey, byte[] rawBody, Map<String, ?> headers) {
        verify(apiKey, rawBody, headers, WebhookOptions.defaults());
    }

    /**
     * Verifies a webhook against a header map.
     *
     * @param headers header names to a {@code String}, a {@code String[]} or a {@code Collection} of strings,
     *                e.g. {@code com.sun.net.httpserver.Headers} or Spring {@code HttpHeaders}; names are
     *                compared case-insensitively (ASCII letters; U+212A matches {@code k}, U+017F matches
     *                {@code s}; other non-ASCII characters match nothing), and values under differently
     *                spelled names count together
     * @throws IllegalArgumentException blank {@code apiKey}, or a header value of another type
     * @throws WebhookHeadersException    signature headers missing, repeated or malformed
     * @throws WebhookTimestampException  timestamp outside the tolerance
     * @throws WebhookSignatureException  signature mismatch
     */
    public static void verify(String apiKey, byte[] rawBody, Map<String, ?> headers, WebhookOptions options) {
        Objects.requireNonNull(headers, "headers");
        check(apiKey, rawBody, options, name -> values(headers, name));
    }

    /** {@link #verify(String, byte[], Function)}, then decodes the body into {@code eventType}. */
    public static <T> T parse(String apiKey, byte[] rawBody, Function<String, String> header, Class<T> eventType) {
        verify(apiKey, rawBody, header);
        return decode(rawBody, eventType);
    }

    /**
     * {@link #verify(String, byte[], Function, WebhookOptions)}, then decodes the body into {@code eventType}.
     *
     * @throws DecodeException the body does not decode into {@code eventType}, including a JSON {@code null}
     */
    public static <T> T parse(String apiKey, byte[] rawBody, Function<String, String> header,
                              WebhookOptions options, Class<T> eventType) {
        verify(apiKey, rawBody, header, options);
        return decode(rawBody, eventType);
    }

    /** {@link #verify(String, byte[], Map)}, then decodes the body into {@code eventType}. */
    public static <T> T parse(String apiKey, byte[] rawBody, Map<String, ?> headers, Class<T> eventType) {
        verify(apiKey, rawBody, headers);
        return decode(rawBody, eventType);
    }

    /**
     * {@link #verify(String, byte[], Map, WebhookOptions)}, then decodes the body into {@code eventType}.
     *
     * @throws DecodeException the body does not decode into {@code eventType}, including a JSON {@code null}
     */
    public static <T> T parse(String apiKey, byte[] rawBody, Map<String, ?> headers, WebhookOptions options,
                              Class<T> eventType) {
        verify(apiKey, rawBody, headers, options);
        return decode(rawBody, eventType);
    }

    private static void check(String apiKey, byte[] rawBody, WebhookOptions options,
                              Function<String, List<String>> lookup) {
        if (RequestSigner.isBlankKey(apiKey)) {
            throw new IllegalArgumentException("API key is required");
        }
        Objects.requireNonNull(rawBody, "rawBody");
        Objects.requireNonNull(options, "options");

        long timestamp = parseTimestamp(single(lookup, TIMESTAMP_HEADER));
        String deliveryId = single(lookup, DELIVERY_HEADER);
        if (!isDeliveryId(deliveryId)) {
            throw malformed(DELIVERY_HEADER);
        }
        byte[] signature = parseSignature(single(lookup, SIGNATURE_HEADER));

        long now = options.clock().instant().getEpochSecond();
        long tolerance = options.tolerance().getSeconds();
        if (!withinTolerance(now, timestamp, tolerance)) {
            throw new WebhookTimestampException("cryptochief: webhook " + TIMESTAMP_HEADER
                    + " is more than " + tolerance + " s from the current time");
        }

        String stringToSign;
        try {
            stringToSign = RequestSigner.webhookV1StringToSign(timestamp, deliveryId, rawBody);
        } catch (IllegalArgumentException e) {
            throw malformed(TIMESTAMP_HEADER);
        }
        if (!MessageDigest.isEqual(hmacSha256(apiKey, stringToSign), signature)) {
            throw new WebhookSignatureException("cryptochief: webhook signature mismatch");
        }
    }

    /** The one value of {@code name}, spaces and tabs trimmed. */
    private static String single(Function<String, List<String>> lookup, String name) {
        List<String> values = lookup.apply(name);
        if (values.size() != 1 || values.get(0) == null) {
            throw malformed(name);
        }
        String value = values.get(0);
        int start = 0;
        int end = value.length();
        while (start < end && isSpaceOrTab(value.charAt(start))) start++;
        while (end > start && isSpaceOrTab(value.charAt(end - 1))) end--;
        value = value.substring(start, end);
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw malformed(name);
        }
        return value;
    }

    private static List<String> values(Map<String, ?> headers, String name) {
        List<String> out = new ArrayList<>(1);
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() == null || !nameMatches(entry.getKey(), name)) continue;
            Object value = entry.getValue();
            if (value == null) {
                out.add(null);
            } else if (value instanceof String s) {
                out.add(s);
            } else if (value instanceof String[] array) {
                out.addAll(Arrays.asList(array));
            } else if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null && !(item instanceof String)) {
                        throw new IllegalArgumentException("header " + name + ": value is not a String");
                    }
                    out.add((String) item);
                }
            } else {
                throw new IllegalArgumentException(
                        "header " + name + ": value is not a String, String[] or Collection");
            }
        }
        return out;
    }

    /**
     * {@code candidate} equals the ASCII {@code name} under simple case folding: ASCII letters in either case,
     * U+212A KELVIN SIGN for {@code k}, U+017F LATIN SMALL LETTER LONG S for {@code s}; any other non-ASCII
     * character matches nothing.
     */
    private static boolean nameMatches(String candidate, String name) {
        if (candidate.length() != name.length()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = candidate.charAt(i);
            char n = asciiLower(name.charAt(i));
            if (asciiLower(c) == n || (c == 'K' && n == 'k') || (c == 'ſ' && n == 's')) continue;
            return false;
        }
        return true;
    }

    private static char asciiLower(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }

    /** Decimal digits without a leading zero ({@code "0"} itself allowed); a value beyond {@code long} is malformed. */
    private static long parseTimestamp(String value) {
        if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')) {
            throw malformed(TIMESTAMP_HEADER);
        }
        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = value.charAt(i) - '0';
            if (digit < 0 || digit > 9 || result > (Long.MAX_VALUE - digit) / 10) {
                throw malformed(TIMESTAMP_HEADER);
            }
            result = result * 10 + digit;
        }
        return result;
    }

    /** {@code v1=} and 64 hex digits of any case, decoded. */
    private static byte[] parseSignature(String value) {
        if (!value.startsWith(PREFIX) || value.length() != PREFIX.length() + 64) {
            throw malformed(SIGNATURE_HEADER);
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = hexDigit(value.charAt(PREFIX.length() + 2 * i));
            int lo = hexDigit(value.charAt(PREFIX.length() + 2 * i + 1));
            if (hi < 0 || lo < 0) {
                throw malformed(SIGNATURE_HEADER);
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** Value of an ASCII hex digit, else {@code -1}. */
    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static boolean withinTolerance(long now, long timestamp, long tolerance) {
        long low;
        long high;
        try {
            low = Math.subtractExact(now, tolerance);
        } catch (ArithmeticException e) {
            low = Long.MIN_VALUE;
        }
        try {
            high = Math.addExact(now, tolerance);
        } catch (ArithmeticException e) {
            high = Long.MAX_VALUE;
        }
        return timestamp >= low && timestamp <= high;
    }

    private static boolean isDeliveryId(String s) {
        if (s.isEmpty() || s.length() > 128) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

    private static WebhookHeadersException malformed(String name) {
        return new WebhookHeadersException(
                "cryptochief: webhook header " + name + " is missing, repeated or malformed");
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

    private static <T> T decode(byte[] rawBody, Class<T> eventType) {
        T event;
        try {
            event = Json.MAPPER.readValue(new String(rawBody, StandardCharsets.UTF_8), eventType);
        } catch (JsonProcessingException e) {
            throw new DecodeException("cryptochief: webhook decode failed: " + e.getMessage(), e);
        }
        if (event == null) {
            throw new DecodeException("cryptochief: webhook decode failed: body is null");
        }
        return event;
    }
}
