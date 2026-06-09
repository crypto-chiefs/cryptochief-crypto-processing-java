package com.cryptochief.processing.exceptions;

/** Connection, DNS, TLS, timeout, or read failure. */
public class NetworkException extends CryptoChiefException {
    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
