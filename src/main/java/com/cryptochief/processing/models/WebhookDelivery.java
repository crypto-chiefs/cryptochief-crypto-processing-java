package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One outbound webhook, with every attempt the platform made and the body it sent.
 * {@code null} means "not recorded", distinct from zero or empty.
 *
 * <p>{@code reference} is the object the event was about - the order or static deposit uuid
 * you already hold. {@code supersededBy} names the NEWER event for the same object when there
 * is one; a superseded delivery cannot be resent - resend the latest event instead. Statuses
 * are the constants of {@link WebhookDeliveryStatus}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookDelivery(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("reference") String reference,
        @JsonProperty("target_url") String targetUrl,
        @JsonProperty("status") String status,
        @JsonProperty("attempts") int attempts,
        @JsonProperty("max_attempts") int maxAttempts,
        @JsonProperty("resend_count") int resendCount,
        @JsonProperty("last_error") String lastError,
        @JsonProperty("last_http_status") Integer lastHttpStatus,
        @JsonProperty("next_attempt_at") String nextAttemptAt,
        @JsonProperty("delivered_at") String deliveredAt,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("superseded_by") String supersededBy,
        @JsonProperty("attempt_history") List<WebhookAttempt> attemptHistory,
        @JsonProperty("payload") WebhookPayload payload
) {
    public WebhookDelivery {
        attemptHistory = attemptHistory == null ? List.of() : List.copyOf(attemptHistory);
    }
}
