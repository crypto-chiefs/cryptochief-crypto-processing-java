package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a resend did. On this platform a resend is synchronous: the POST to your endpoint
 * happens before the answer comes back, so {@code queued == true} arrives with {@code status}
 * already {@code delivered} or {@code failed} for that attempt.
 *
 * <p>{@code reason} is set when {@code queued} is false: one of the {@code DELIVERY_*} /
 * {@code RESEND_TOO_SOON} codes in {@link com.cryptochief.processing.exceptions.ErrorCode}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookResendResult(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("reference") String reference,
        @JsonProperty("status") String status,
        @JsonProperty("queued") boolean queued,
        @JsonProperty("attempts") int attempts,
        @JsonProperty("resend_count") int resendCount,
        @JsonProperty("reason") String reason,
        @JsonProperty("superseded_by") String supersededBy,
        @JsonProperty("retry_after_seconds") Integer retryAfterSeconds
) {}
