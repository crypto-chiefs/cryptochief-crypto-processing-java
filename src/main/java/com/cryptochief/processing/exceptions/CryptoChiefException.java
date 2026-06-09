package com.cryptochief.processing.exceptions;

/** Root of every exception thrown by the SDK. */
public abstract class CryptoChiefException extends RuntimeException {
    protected CryptoChiefException(String message) {
        super(message);
    }

    protected CryptoChiefException(String message, Throwable cause) {
        super(message, cause);
    }
}
