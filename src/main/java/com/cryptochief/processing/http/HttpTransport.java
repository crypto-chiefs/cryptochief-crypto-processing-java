package com.cryptochief.processing.http;

import com.cryptochief.processing.Options;
import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.DecodeException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.exceptions.NetworkException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal HTTP pipeline: signs each request, retries 5xx + transport failures, parses error envelope.
 *
 * <p>HMAC v1 headers are computed per attempt. On {@code SIGNATURE_TIMESTAMP_OUT_OF_RANGE} with
 * {@code server_time} the clock offset is corrected and the request is repeated once.
 *
 * <p>Service calls go out as {@code POST}; {@link #request} takes the method, for a route the SDK has no
 * method for. {@link #withIdempotencyKey} returns a view that sends and signs {@code Idempotency-Key}.
 */
public final class HttpTransport {

    private static final Logger LOG = LoggerFactory.getLogger("com.cryptochief.processing");
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final String HEADER_MERCHANT = "Merchant";
    private static final byte[] EMPTY_BODY = new byte[0];

    private final Options options;
    private final OkHttpClient http;
    private final boolean ownsHttp;
    /** {@code Idempotency-Key} sent with every request, or {@code null}. */
    private final String idempotencyKey;
    /** Server time minus local time, seconds; set from {@code server_time}. Shared with every view. */
    private final AtomicLong clockOffsetSeconds;

    public HttpTransport(Options options) {
        this.options = options;
        if (options.httpClient() != null) {
            this.http = options.httpClient();
            this.ownsHttp = false;
        } else {
            this.http = defaultClient(options);
            this.ownsHttp = true;
        }
        this.idempotencyKey = null;
        this.clockOffsetSeconds = new AtomicLong();
    }

    /** A view of {@code base} over the same HTTP client and clock offset, which it does not own. */
    private HttpTransport(HttpTransport base, String idempotencyKey) {
        this.options = base.options;
        this.http = base.http;
        this.ownsHttp = false;
        this.idempotencyKey = idempotencyKey;
        this.clockOffsetSeconds = base.clockOffsetSeconds;
    }

    /**
     * A view that sends {@code Idempotency-Key} on every request it makes. The header is part of the string
     * to sign, so it has to be set before signing: one added by an {@link okhttp3.Interceptor} is not covered
     * by the signature and the server answers {@code INVALID_SIGNATURE}.
     *
     * <p>The value must be printable ASCII with no space at either edge - the server trims spaces and tabs
     * before signing, so an untrimmed value would be signed in a form it never sees. {@code null} or empty
     * returns a view that sends no header.
     *
     * @throws IllegalArgumentException the key cannot be sent as it is
     */
    public HttpTransport withIdempotencyKey(String key) {
        if (key == null || key.isEmpty()) {
            return new HttpTransport(this, null);
        }
        if (!isSendableIdempotencyKey(key)) {
            throw new IllegalArgumentException("cryptochief: Idempotency-Key must be printable ASCII without a "
                    + "leading or trailing space or tab: \"" + key + "\"");
        }
        return new HttpTransport(this, key);
    }

    /** Printable ASCII, no space or tab at either edge. */
    private static boolean isSendableIdempotencyKey(String key) {
        if (key.charAt(0) == ' ' || key.charAt(key.length() - 1) == ' ') {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c > 0x7E) return false;
        }
        return true;
    }

    /** The {@code Idempotency-Key} this transport sends, or {@code null}. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    public OkHttpClient http() {
        return http;
    }

    public boolean ownsHttpClient() {
        return ownsHttp;
    }

    /** Sign, send, decode into the given class. */
    public <T> T send(String path, Object body, Class<T> responseType) {
        return request("POST", path, body, responseType);
    }

    /**
     * Sign, send, decode into the given class, with the HTTP method spelled out. {@code path} starts with
     * {@code "/"} and carries a query as {@code "?a=1&b=2"}: the path is signed percent-decoded, the query as
     * written. A {@code null} body sends none, which is what a {@code GET} takes.
     */
    public <T> T request(String method, String path, Object body, Class<T> responseType) {
        byte[] raw = requestRaw(method, path, body);
        if (raw.length == 0) {
            throw new DecodeException("cryptochief: empty response body from " + path);
        }
        try {
            return Json.MAPPER.readValue(new String(raw, StandardCharsets.UTF_8), responseType);
        } catch (JsonProcessingException e) {
            throw new DecodeException("cryptochief: decode " + path + " response: " + e.getMessage(), e);
        }
    }

    /** {@link #request(String, String, Object, Class)} into a generic type. */
    public <T> T request(String method, String path, Object body, TypeReference<T> responseType) {
        byte[] raw = requestRaw(method, path, body);
        if (raw.length == 0) {
            throw new DecodeException("cryptochief: empty response body from " + path);
        }
        try {
            return Json.MAPPER.readValue(new String(raw, StandardCharsets.UTF_8), responseType);
        } catch (JsonProcessingException e) {
            throw new DecodeException("cryptochief: decode " + path + " response: " + e.getMessage(), e);
        }
    }

    /**
     * Sign, send, decode a list-shaped answer, and hand back an empty list where the service
     * answered with literal JSON {@code null}.
     *
     * <p>Several endpoints are served by Go handlers that build their result with a nil
     * slice, which marshals as {@code null} rather than {@code []} when nothing matched.
     * {@code /v1/blockchains/list} and {@code /v1/currencies/fiats} are two of them. A method
     * whose signature promises a {@code List} must return an empty one for that body - never
     * {@code null}, and never a decode failure - so callers can iterate the answer without
     * asking whether "no rows" arrived as absence or as emptiness.
     */
    public <T> List<T> sendList(String path, Object body, TypeReference<List<T>> responseType) {
        List<T> decoded = send(path, body, responseType);
        return decoded == null ? List.of() : decoded;
    }

    /** Sign, send, decode into a generic type. */
    public <T> T send(String path, Object body, TypeReference<T> responseType) {
        return request("POST", path, body, responseType);
    }

    /**
     * Request body as sent and signed: {@code body} serialised with {@link Json#MAPPER}, which omits {@code null}
     * properties and {@code null} map values. {@code null} is the empty body.
     */
    private static byte[] requestBody(Object body) {
        if (body == null) return EMPTY_BODY;
        try {
            return Json.MAPPER.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("cryptochief: encode request body: " + e.getMessage(), e);
        }
    }

    /**
     * Sign, send, and hand back the response bytes. {@code method} goes on the wire and into the string to
     * sign in the same form, {@code a-z} upper-cased; a {@code GET} or {@code HEAD} carries no body.
     */
    public byte[] requestRaw(String rawMethod, String path, Object body) {
        if (rawMethod == null || rawMethod.isEmpty()) {
            throw new IllegalArgumentException("cryptochief: request method is required");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("cryptochief: request path must start with \"/\": " + path);
        }
        String method = RequestSigner.upperAsciiMethod(rawMethod);
        byte[] payload = requestBody(body);
        String url = options.baseUrl() + path;
        int queryStart = path.indexOf('?');
        String rawPath = queryStart < 0 ? path : path.substring(0, queryStart);
        // The server signs the path it reads, which is percent-decoded; the escaped spelling is what
        // goes on the wire.
        String routePath = RequestSigner.decodePath(rawPath);
        boolean bodyless = "GET".equals(method) || "HEAD".equals(method);
        int attempts = options.maxRetries() + 1;
        RuntimeException lastException = null;
        boolean clockCorrected = false;
        boolean skipBackoff = false;

        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0 && !skipBackoff) {
                long backoffMs = Backoff.delay(attempt, options.initialRetryDelay(), options.maxRetryDelay())
                        .toMillis();
                LOG.debug("cryptochief retry attempt={} delay={}ms path={}", attempt, backoffMs, path);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NetworkException("cryptochief: interrupted while retrying", e);
                }
            }

            skipBackoff = false;

            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .method(method, bodyless ? null : RequestBody.create(payload, APPLICATION_JSON))
                    .header("Accept", "application/json")
                    .header("User-Agent", options.userAgent())
                    .header(HEADER_MERCHANT, options.merchantId());
            if (!bodyless) {
                builder.header("Content-Type", "application/json");
            }
            if (idempotencyKey != null) {
                builder.header(RequestSigner.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
            }
            Request request = signHmacV1(builder.build(), routePath, payload);

            int status;
            byte[] bytes;
            try (Response response = http.newCall(request).execute()) {
                status = response.code();
                ResponseBody respBody = response.body();
                bytes = respBody == null ? new byte[0] : respBody.bytes();
            } catch (IOException e) {
                NetworkException netErr = new NetworkException(
                        "cryptochief: request failed: " + e.getMessage(), e);
                lastException = netErr;
                if (attempt + 1 < attempts) continue;
                throw netErr;
            }
            LOG.debug("cryptochief response path={} status={} bytes={}", path, status, bytes.length);

            if (status >= 200 && status < 300) {
                return bytes;
            }

            ParsedError parsed = parseApiError(status, bytes);
            ApiException apiErr = parsed.exception();
            if (!clockCorrected && correctClock(parsed)) {
                LOG.debug("cryptochief clock offset corrected offset={}s path={}", clockOffsetSeconds.get(), path);
                clockCorrected = true;
                skipBackoff = true;
                attempts++;
                continue;
            }
            if (status >= 500 && attempt + 1 < attempts) {
                lastException = apiErr;
                continue;
            }
            throw apiErr;
        }
        throw lastException != null ? lastException
                : new NetworkException("cryptochief: retry budget exhausted");
    }

    private Request signHmacV1(Request request, String routePath, byte[] body) {
        String timestamp = Long.toString(System.currentTimeMillis() / 1000L + clockOffsetSeconds.get());
        String nonce = RequestSigner.newNonce();
        HttpUrl requestUrl = request.url();
        String hmac = RequestSigner.signHmacV1(options.apiKey(), timestamp, nonce, request.method(), routePath,
                requestUrl.encodedQuery(), request.header(HEADER_MERCHANT),
                request.header(RequestSigner.HEADER_IDEMPOTENCY_KEY), body);
        return request.newBuilder()
                .header(RequestSigner.HEADER_TIMESTAMP, timestamp)
                .header(RequestSigner.HEADER_NONCE, nonce)
                .header(RequestSigner.HEADER_HMAC_SIGNATURE, RequestSigner.HMAC_V1_PREFIX + hmac)
                .build();
    }

    /** Applies {@code server_time} from a {@code SIGNATURE_TIMESTAMP_OUT_OF_RANGE} refusal; {@code false} if absent. */
    private boolean correctClock(ParsedError parsed) {
        if (!ErrorCode.SIGNATURE_TIMESTAMP_OUT_OF_RANGE.equals(parsed.exception().code())
                || parsed.serverTime() == null) {
            return false;
        }
        clockOffsetSeconds.set(parsed.serverTime() - System.currentTimeMillis() / 1000L);
        return true;
    }

    /** A non-2xx response: the exception to throw and {@code server_time} (Unix seconds) if the body has one. */
    private record ParsedError(ApiException exception, Long serverTime) {}

    /**
     * Reads code, message and {@code server_time} from both envelopes.
     *
     * <p>Gateway: {@code {"ok":false,"error":"<CODE>","msg":"...","server_time":...}}.
     * White-label: {@code {"data":null,"error":{"status":...,"name":...,"message":"...",
     * "details":{"code":"<CODE>","server_time":...}},"server_time":...}}; the code is
     * {@code error.details.code}, else {@code error.name}.
     */
    private ParsedError parseApiError(int status, byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        String code = null;
        String message = null;
        Long serverTime = null;
        try {
            JsonNode node = Json.MAPPER.readTree(text);
            if (node != null && node.isObject() && node.path("error").isObject()) {
                JsonNode error = node.get("error");
                JsonNode details = error.path("details");
                code = textField(details, "code");
                if (code == null || code.isEmpty()) {
                    code = textField(error, "name");
                }
                message = textField(error, "message");
                serverTime = longField(node, "server_time");
                if (serverTime == null) {
                    serverTime = longField(details, "server_time");
                }
            } else if (node != null && node.isObject()) {
                serverTime = longField(node, "server_time");
                String errorField = textField(node, "error");
                String msgField = textField(node, "msg");
                // Two envelope shapes, one code. When "error" names the refusal itself
                // (LABEL_TOO_LONG, INVALID_PARAMS, ...) that is the code and "msg" is an
                // English sentence for a human. When "error" is the generic SERVICE_ERROR
                // marker, the refusal came from an upstream service and names itself in
                // "msg" instead. Do not "simplify" this to preferring msg: that hands the
                // caller a sentence and every gateway-side ErrorCode constant stops
                // matching.
                if (msgField == null || msgField.isEmpty() || msgField.equals(errorField)) {
                    code = errorField;
                } else if (errorField == null || errorField.isEmpty()
                        || ErrorCode.SERVICE_ERROR.equals(errorField)) {
                    code = msgField;
                } else {
                    code = errorField;
                    message = msgField;
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        String finalCode = (code == null || code.isEmpty()) ? "HTTP_" + status : code;
        String finalMessage = (message == null || message.isEmpty()) ? finalCode : message;
        String truncated = text.length() <= 8192 ? text : text.substring(0, 8192) + "…";
        return new ParsedError(new ApiException(finalCode, status, finalMessage, truncated), serverTime);
    }

    private static String textField(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Long longField(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? value.asLong() : null;
    }

    private static OkHttpClient defaultClient(Options options) {
        return new OkHttpClient.Builder()
                .callTimeout(options.requestTimeout())
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(options.requestTimeout())
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
}
