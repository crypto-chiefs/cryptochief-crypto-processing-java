package com.cryptochief.processing;

import okhttp3.OkHttpClient;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Objects;

/** Immutable configuration for {@link CryptoChiefClient}. */
public final class Options {

    public static final String DEFAULT_BASE_URL = "https://api-processing.crypto-chief.com";
    public static final String DEFAULT_TON_RPC_BASE_URL = "https://rpc.crypto-chief.com";

    private final String merchantId;
    private final String apiKey;
    private final String baseUrl;
    private final String tonRpcBaseUrl;
    private final String userAgent;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;
    private final RSAPrivateKey rsaPrivateKey;
    private final OkHttpClient httpClient;

    private Options(Builder b) {
        Objects.requireNonNull(b.merchantId, "merchantId");
        Objects.requireNonNull(b.apiKey, "apiKey");
        if (b.merchantId.isBlank()) throw new IllegalArgumentException("merchantId is required");
        if (b.apiKey.isBlank()) throw new IllegalArgumentException("apiKey is required");
        if (b.baseUrl == null || b.baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl is required");
        if (b.maxRetries < 0) throw new IllegalArgumentException("maxRetries cannot be negative");

        this.merchantId = b.merchantId;
        this.apiKey = b.apiKey;
        this.baseUrl = trimTrailingSlash(b.baseUrl);
        this.tonRpcBaseUrl = trimTrailingSlash(b.tonRpcBaseUrl);
        this.userAgent = b.userAgent;
        this.requestTimeout = b.requestTimeout;
        this.maxRetries = b.maxRetries;
        this.initialRetryDelay = b.initialRetryDelay;
        this.maxRetryDelay = b.maxRetryDelay;
        this.rsaPrivateKey = b.rsaPrivateKey;
        this.httpClient = b.httpClient;
    }

    public String merchantId() { return merchantId; }
    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public String tonRpcBaseUrl() { return tonRpcBaseUrl; }
    public String userAgent() { return userAgent; }
    public Duration requestTimeout() { return requestTimeout; }
    public int maxRetries() { return maxRetries; }
    public Duration initialRetryDelay() { return initialRetryDelay; }
    public Duration maxRetryDelay() { return maxRetryDelay; }
    public RSAPrivateKey rsaPrivateKey() { return rsaPrivateKey; }
    public OkHttpClient httpClient() { return httpClient; }

    public static Builder builder() {
        return new Builder();
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return null;
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }

    public static final class Builder {
        private String merchantId;
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private String tonRpcBaseUrl = DEFAULT_TON_RPC_BASE_URL;
        private String userAgent = "cryptochief-java/" + BuildInfo.VERSION;
        private Duration requestTimeout = Duration.ofSeconds(60);
        private int maxRetries = 3;
        private Duration initialRetryDelay = Duration.ofMillis(200);
        private Duration maxRetryDelay = Duration.ofSeconds(5);
        private RSAPrivateKey rsaPrivateKey;
        private OkHttpClient httpClient;

        public Builder merchantId(String v) { this.merchantId = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder tonRpcBaseUrl(String v) { this.tonRpcBaseUrl = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public Builder requestTimeout(Duration v) { this.requestTimeout = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder initialRetryDelay(Duration v) { this.initialRetryDelay = v; return this; }
        public Builder maxRetryDelay(Duration v) { this.maxRetryDelay = v; return this; }
        public Builder rsaPrivateKey(RSAPrivateKey v) { this.rsaPrivateKey = v; return this; }
        public Builder httpClient(OkHttpClient v) { this.httpClient = v; return this; }

        public Options build() {
            return new Options(this);
        }
    }
}
