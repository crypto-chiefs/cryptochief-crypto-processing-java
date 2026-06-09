package com.cryptochief.processing.http;

import com.cryptochief.processing.Options;
import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.exceptions.DecodeException;
import com.cryptochief.processing.exceptions.ErrorCode;
import com.cryptochief.processing.exceptions.NetworkException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.TimeUnit;

/** Internal HTTP pipeline: signs each request, retries 5xx + transport failures, parses error envelope. */
public final class HttpTransport {

    private static final Logger LOG = LoggerFactory.getLogger("com.cryptochief.processing");
    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final String HEADER_MERCHANT = "Merchant";
    private static final String HEADER_SIGNATURE = "Signature";

    private final Options options;
    private final OkHttpClient http;
    private final boolean ownsHttp;

    public HttpTransport(Options options) {
        this.options = options;
        if (options.httpClient() != null) {
            this.http = options.httpClient();
            this.ownsHttp = false;
        } else {
            this.http = defaultClient(options);
            this.ownsHttp = true;
        }
    }

    public OkHttpClient http() {
        return http;
    }

    public boolean ownsHttpClient() {
        return ownsHttp;
    }

    /** Sign, send, decode into the given class. */
    public <T> T send(String path, Object body, Class<T> responseType) {
        byte[] raw = sendRaw(path, body);
        if (raw.length == 0) {
            throw new DecodeException("cryptochief: empty response body from " + path);
        }
        try {
            return CanonicalJson.MAPPER.readValue(new String(raw, StandardCharsets.UTF_8), responseType);
        } catch (JsonProcessingException e) {
            throw new DecodeException("cryptochief: decode " + path + " response: " + e.getMessage(), e);
        }
    }

    /** Sign, send, decode into a generic type. */
    public <T> T send(String path, Object body, TypeReference<T> responseType) {
        byte[] raw = sendRaw(path, body);
        if (raw.length == 0) {
            throw new DecodeException("cryptochief: empty response body from " + path);
        }
        try {
            return CanonicalJson.MAPPER.readValue(new String(raw, StandardCharsets.UTF_8), responseType);
        } catch (JsonProcessingException e) {
            throw new DecodeException("cryptochief: decode " + path + " response: " + e.getMessage(), e);
        }
    }

    private byte[] sendRaw(String path, Object body) {
        byte[] canonical = CanonicalJson.encode(body);
        String signature = RequestSigner.sign(canonical, options.apiKey());
        String url = options.baseUrl() + path;
        int attempts = options.maxRetries() + 1;
        RuntimeException lastException = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0) {
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

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(canonical, APPLICATION_JSON))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", options.userAgent())
                    .header(HEADER_MERCHANT, options.merchantId())
                    .header(HEADER_SIGNATURE, signature)
                    .build();

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

            ApiException apiErr = parseApiError(status, bytes);
            if (status >= 500 && attempt + 1 < attempts) {
                lastException = apiErr;
                continue;
            }
            throw apiErr;
        }
        throw lastException != null ? lastException
                : new NetworkException("cryptochief: retry budget exhausted");
    }

    private ApiException parseApiError(int status, byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        String code = null;
        String message = null;
        try {
            JsonNode node = CanonicalJson.MAPPER.readTree(text);
            if (node != null && node.isObject()) {
                String errorField = node.has("error") && node.get("error").isTextual()
                        ? node.get("error").asText() : null;
                String msgField = node.has("msg") && node.get("msg").isTextual()
                        ? node.get("msg").asText() : null;
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
        String finalMessage = message == null ? finalCode : message;
        String truncated = text.length() <= 8192 ? text : text.substring(0, 8192) + "…";
        return new ApiException(finalCode, status, finalMessage, truncated);
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
