package com.cryptochief.processing.exceptions;

/** Missing or malformed configuration: merchant ID, API key, RSA key. */
public class ConfigurationException extends CryptoChiefException {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
