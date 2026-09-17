package com.cryptochief.processing.webhook;

/** {@code X-CC-Signature} does not match the body, timestamp and delivery id. */
public class WebhookSignatureException extends WebhookVerificationException {
    public WebhookSignatureException(String message) {
        super(message);
    }
}
