package com.cryptochief.processing.exceptions;

/** Server returned a non-2xx response with a structured error envelope. */
public class ApiException extends CryptoChiefException {

    private final String code;
    private final int status;
    private final String description;
    private final String raw;

    public ApiException(String code, int status, String description, String raw) {
        super(buildMessage(code, status, description));
        this.code = code;
        this.status = status;
        this.description = description;
        this.raw = raw;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String description() {
        return description;
    }

    public String raw() {
        return raw;
    }

    /** True if the SDK considers this error transient and worth retrying. */
    public boolean retryable() {
        return (status >= 500 && status <= 599) || ErrorCode.NETWORK_ERROR.equals(code);
    }

    private static String buildMessage(String code, int status, String description) {
        if (status == 0) {
            return "cryptochief: " + code;
        }
        if (description != null && !description.isEmpty() && !description.equals(code)) {
            return "cryptochief: " + status + " " + code + ": " + description;
        }
        return "cryptochief: " + status + " " + code;
    }
}
