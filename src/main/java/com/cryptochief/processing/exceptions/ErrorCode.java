package com.cryptochief.processing.exceptions;

/** Known stable error code strings used in {@link ApiException#code()}. */
public final class ErrorCode {
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS";
    public static final String DEBT_LIMIT_EXCEEDED = "DEBT_LIMIT_EXCEEDED";
    public static final String ASSET_NOT_ENABLED = "ASSET_NOT_ENABLED";
    public static final String ORDER_ALREADY_EXIST = "ORDER_ALREADY_EXIST";
    public static final String ORDER_CANNOT_CANCEL = "ORDER_CANNOT_CANCEL";
    public static final String ORDER_NOT_LIVE = "ORDER_NOT_LIVE";
    public static final String ASSET_ALREADY_SELECTED = "ASSET_ALREADY_SELECTED";
    public static final String INVALID_PARAMS = "INVALID_PARAMS";
    public static final String SERVICE_ERROR = "SERVICE_ERROR";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String URL_CALLBACK_REQUIRED = "URL_CALLBACK_REQUIRED";
    public static final String BATCH_EMPTY = "BATCH_EMPTY";
    public static final String BATCH_TOO_LARGE = "BATCH_TOO_LARGE";
    public static final String BATCH_DUPLICATE_ORDER_ID = "BATCH_DUPLICATE_ORDER_ID";
    public static final String FROM_WALLET_NOT_OWNED = "FROM_WALLET_NOT_OWNED";
    public static final String SIGNATURE_EXPIRED = "SIGNATURE_EXPIRED";
    public static final String ALREADY_EXECUTED = "ALREADY_EXECUTED";
    public static final String PREFLIGHT_FAILED = "PREFLIGHT_FAILED";
    public static final String BROADCAST_FAILED = "BROADCAST_FAILED";
    public static final String SIGNED_TX_MISMATCH = "SIGNED_TX_MISMATCH";
    public static final String CONTRACT_REQUIRED_FOR_TOKEN = "CONTRACT_REQUIRED_FOR_TOKEN";
    public static final String TRANSFER_FIELDS_NOT_ALLOWED_FOR_CONTRACT =
            "TRANSFER_FIELDS_NOT_ALLOWED_FOR_CONTRACT";
    public static final String CALLS_REQUIRED = "CALLS_REQUIRED";
    public static final String CALLS_NOT_ALLOWED_FOR_TRANSFER = "CALLS_NOT_ALLOWED_FOR_TRANSFER";
    public static final String CONTRACT_CALLS_UNSUPPORTED_ON_NETWORK =
            "CONTRACT_CALLS_UNSUPPORTED_ON_NETWORK";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";

    private ErrorCode() {}
}
