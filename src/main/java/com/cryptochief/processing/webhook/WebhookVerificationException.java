package com.cryptochief.processing.webhook;

import com.cryptochief.processing.exceptions.CryptoChiefException;

/**
 * A webhook failed verification. The subclass names the reason: {@link WebhookHeadersException},
 * {@link WebhookTimestampException}, {@link WebhookSignatureException}. Answer the sender with 401.
 */
public abstract class WebhookVerificationException extends CryptoChiefException {
    protected WebhookVerificationException(String message) {
        super(message);
    }
}
