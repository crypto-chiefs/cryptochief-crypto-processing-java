package com.cryptochief.processing.webhook;

/**
 * {@code X-CC-Timestamp}, {@code X-Webhook-Delivery} or {@code X-CC-Signature} is missing, repeated or
 * malformed.
 */
public class WebhookHeadersException extends WebhookVerificationException {
    public WebhookHeadersException(String message) {
        super(message);
    }
}
