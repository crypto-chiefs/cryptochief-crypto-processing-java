package com.cryptochief.processing.webhook;

/** {@code X-CC-Timestamp} is outside the tolerance around the current time. */
public class WebhookTimestampException extends WebhookVerificationException {
    public WebhookTimestampException(String message) {
        super(message);
    }
}
