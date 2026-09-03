package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One POST the platform made to your endpoint. Newest first in
 * {@link WebhookDelivery#attemptHistory()}.
 *
 * <p>{@code httpStatus} is {@code null} when nothing answered (DNS, connect, TLS, timeout) -
 * {@code error} then holds the transport error. {@code createdAt} is {@code null} for
 * attempts recorded before the platform kept the time. {@code responseBody} is what your
 * endpoint answered, as the platform saw it, capped; {@code responseTruncated} says whether
 * it was cut.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookAttempt(
        @JsonProperty("attempt") int attempt,
        @JsonProperty("http_status") Integer httpStatus,
        @JsonProperty("error") String error,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("target_url") String targetUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("response_body") String responseBody,
        @JsonProperty("response_content_type") String responseContentType,
        @JsonProperty("response_truncated") boolean responseTruncated
) {}
