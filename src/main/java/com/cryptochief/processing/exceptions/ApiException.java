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

    /**
     * The machine-readable code, and the one field to branch on.
     *
     * <p>The wire has two envelope shapes and this resolves both. A refusal the gateway
     * decided itself names itself in {@code error} and puts an English sentence in
     * {@code msg} - {@code {"error":"LABEL_TOO_LONG","msg":"label is longer than 255
     * characters"}} - and the code is {@code LABEL_TOO_LONG}. A refusal relayed from an
     * upstream service arrives as {@code error: "SERVICE_ERROR"} with the real token in
     * {@code msg} - {@code {"error":"SERVICE_ERROR","msg":"wallet_not_found"}} - and the
     * code is {@code wallet_not_found}. Either way one switch over {@link ErrorCode}
     * constants and upstream tokens is enough.
     *
     * <p>Falls back to {@code HTTP_<status>} when the body carries neither field.
     */
    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    /**
     * The human-readable half: the {@code msg} sentence when the gateway sent one, and
     * otherwise the same string as {@link #code()}. For logs and support tickets, never
     * for branching - the wording is not stable.
     */
    public String description() {
        return description;
    }

    /** The response body verbatim, truncated at 8&nbsp;KiB. Nothing is dropped before that. */
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
