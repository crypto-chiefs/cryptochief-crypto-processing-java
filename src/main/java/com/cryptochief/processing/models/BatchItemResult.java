package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One result from a batch payout estimate or execute request.
 * {@code httpStatus} is absent for successful items and older responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchItemResult(
        @JsonProperty("index") int index,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("status") String status,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("error") String error,
        @JsonProperty("http_status") Integer httpStatus
) {
    /** The constructor without an HTTP status; {@code httpStatus} is {@code null}. */
    public BatchItemResult(int index, String orderId, String status, String uuid, String error) {
        this(index, orderId, status, uuid, error, null);
    }

    /** True only for HTTP 502. Retry execute items with the same {@code order_id}. */
    public boolean retryable() {
        return httpStatus != null && httpStatus == 502;
    }
}
