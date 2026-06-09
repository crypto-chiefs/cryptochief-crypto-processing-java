package com.cryptochief.processing.exceptions;

/** Response was 2xx but the body did not parse against the expected schema. */
public class DecodeException extends CryptoChiefException {
    public DecodeException(String message) {
        super(message);
    }

    public DecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
