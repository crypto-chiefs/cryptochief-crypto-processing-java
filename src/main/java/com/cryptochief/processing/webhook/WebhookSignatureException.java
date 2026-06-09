package com.cryptochief.processing.webhook;

/** Signature header did not match the body. */
public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException(String message) {
        super(message);
    }
}
