package com.cryptochief.processing.exceptions;

/**
 * Known stable error code strings used in {@link ApiException#code()}.
 *
 * <p>Two envelope shapes reach this one field. When the gateway names the refusal itself the
 * code is in {@code error}; when {@code error} is the generic {@code SERVICE_ERROR} of a
 * refusal relayed from an upstream service, the code is in {@code msg}. Constants below come
 * from both, which is why {@link #WALLET_NOT_FOUND} and {@link #SWEEP_SETTINGS_LOCKED} sit
 * beside {@link #INVALID_PARAMS}.
 *
 * <p>Not the whole set: some upstream refusals carry their own lower-case tokens
 * ({@code master_wallet_frozen}, ...) and reach {@link ApiException#code()} the same way.
 * Compare against those as string literals.
 */
public final class ErrorCode {

    // --- Request envelope and transport -------------------------------------------------
    public static final String INVALID_PARAMS = "INVALID_PARAMS";
    public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
    public static final String SIGNATURE_EXPIRED = "SIGNATURE_EXPIRED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String SERVICE_ERROR = "SERVICE_ERROR";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    /** A field the endpoint's schema does not declare. Not silently discarded, because the field most often mistyped is the master wallet and discarding it sends money elsewhere. */
    public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    public static final String INVALID_DATE_FROM = "INVALID_DATE_FROM";

    // --- Balances and credits -----------------------------------------------------------
    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    public static final String INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS";
    public static final String DEBT_LIMIT_EXCEEDED = "DEBT_LIMIT_EXCEEDED";
    public static final String AMOUNT_OUT_OF_RANGE = "AMOUNT_OUT_OF_RANGE";
    public static final String UNSUPPORTED_CURRENCY = "UNSUPPORTED_CURRENCY";
    public static final String INVALID_URL = "INVALID_URL";
    public static final String TOPUP_NOT_CONFIGURED = "TOPUP_NOT_CONFIGURED";

    // --- Assets and environments --------------------------------------------------------
    public static final String ASSET_NOT_ENABLED = "ASSET_NOT_ENABLED";
    /** {@code environment} was neither {@code mainnet} nor {@code testnet}. A typo is refused rather than resolved to the project default. */
    public static final String ENVIRONMENT_INVALID = "ENVIRONMENT_INVALID";
    /** {@code environment: "testnet"} on a project where test networks are not enabled. */
    public static final String TESTNET_NOT_ALLOWED = "TESTNET_NOT_ALLOWED";

    // --- Orders -------------------------------------------------------------------------
    public static final String ORDER_ALREADY_EXIST = "ORDER_ALREADY_EXIST";
    public static final String ORDER_CANNOT_CANCEL = "ORDER_CANNOT_CANCEL";
    public static final String ORDER_NOT_LIVE = "ORDER_NOT_LIVE";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ASSET_ALREADY_SELECTED = "ASSET_ALREADY_SELECTED";
    public static final String URL_CALLBACK_REQUIRED = "URL_CALLBACK_REQUIRED";
    public static final String USER_ID_REQUIRED = "USER_ID_REQUIRED";
    public static final String TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String WITHDRAW_NOT_FOUND = "WITHDRAW_NOT_FOUND";

    // --- Wallets ------------------------------------------------------------------------
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";
    public static final String LABEL_TOO_LONG = "LABEL_TOO_LONG";
    public static final String FROM_WALLET_NOT_OWNED = "FROM_WALLET_NOT_OWNED";
    public static final String FROM_WALLET_FROZEN = "FROM_WALLET_FROZEN";
    /** A white-label installation refuses to guess a master wallet rather than falling back to the oldest of the chain family. */
    public static final String MASTER_WALLET_REQUIRED = "MASTER_WALLET_REQUIRED";
    public static final String MASTER_WALLET_AMBIGUOUS = "MASTER_WALLET_AMBIGUOUS";
    public static final String NO_MASTER_WALLETS = "NO_MASTER_WALLETS";

    // --- Payouts ------------------------------------------------------------------------
    public static final String FEE_LIMIT_EXCEEDED = "FEE_LIMIT_EXCEEDED";
    public static final String FAILED_TO_ESTIMATE_FEE = "FAILED_TO_ESTIMATE_FEE";
    public static final String BATCH_EMPTY = "BATCH_EMPTY";
    public static final String BATCH_TOO_LARGE = "BATCH_TOO_LARGE";
    public static final String BATCH_DUPLICATE_ORDER_ID = "BATCH_DUPLICATE_ORDER_ID";

    // --- Sign / execute -----------------------------------------------------------------
    public static final String ALREADY_EXECUTED = "ALREADY_EXECUTED";
    public static final String PREFLIGHT_FAILED = "PREFLIGHT_FAILED";
    public static final String BROADCAST_FAILED = "BROADCAST_FAILED";
    public static final String SIGN_FAILED = "SIGN_FAILED";
    public static final String SIGNED_TX_MISMATCH = "SIGNED_TX_MISMATCH";
    public static final String TYPE_INVALID = "TYPE_INVALID";
    public static final String TO_ADDRESS_REQUIRED = "TO_ADDRESS_REQUIRED";
    public static final String VALUE_REQUIRED = "VALUE_REQUIRED";
    public static final String CONTRACT_REQUIRED_FOR_TOKEN = "CONTRACT_REQUIRED_FOR_TOKEN";
    public static final String CONTRACT_NOT_ALLOWED_FOR_NATIVE = "CONTRACT_NOT_ALLOWED_FOR_NATIVE";
    public static final String TRANSFER_FIELDS_NOT_ALLOWED_FOR_CONTRACT =
            "TRANSFER_FIELDS_NOT_ALLOWED_FOR_CONTRACT";
    public static final String CALLS_REQUIRED = "CALLS_REQUIRED";
    public static final String CALLS_NOT_ALLOWED_FOR_TRANSFER = "CALLS_NOT_ALLOWED_FOR_TRANSFER";
    public static final String CONTRACT_CALLS_UNSUPPORTED_ON_NETWORK =
            "CONTRACT_CALLS_UNSUPPORTED_ON_NETWORK";
    public static final String SINGLE_CALL_REQUIRED = "SINGLE_CALL_REQUIRED";
    public static final String CALL_TO_REQUIRED = "CALL_TO_REQUIRED";
    public static final String CALL_EMPTY = "CALL_EMPTY";
    public static final String CALL_DATA_MUST_BE_HEX = "CALL_DATA_MUST_BE_HEX";
    public static final String CALL_DATA_MUST_BE_BASE64 = "CALL_DATA_MUST_BE_BASE64";

    // --- Auto-sweep settings ------------------------------------------------------------
    public static final String TYPE_WORK_INVALID = "TYPE_WORK_INVALID";
    public static final String FEE_MODE_INVALID = "FEE_MODE_INVALID";
    public static final String THRESHOLD_INVALID = "THRESHOLD_INVALID";
    public static final String THRESHOLD_MUST_BE_POSITIVE = "THRESHOLD_MUST_BE_POSITIVE";
    public static final String THRESHOLD_REQUIRED_FOR_THRESHOLD_MODE =
            "THRESHOLD_REQUIRED_FOR_THRESHOLD_MODE";
    /** An operator pinned the policy; a merchant write changes nothing while it is set. */
    public static final String SWEEP_SETTINGS_LOCKED = "SWEEP_SETTINGS_LOCKED";

    private ErrorCode() {}

    /** The object does not exist OR is not this project's - deliberately indistinguishable. */
    public static final String NOT_FOUND = "NOT_FOUND";
    /** Webhook resend: a newer event exists for the same object; only the latest may be resent. Permanent. */
    public static final String DELIVERY_SUPERSEDED = "DELIVERY_SUPERSEDED";
    /** Webhook resend: a worker holds the delivery, or it is already scheduled for a retry. */
    public static final String DELIVERY_IN_FLIGHT = "DELIVERY_IN_FLIGHT";
    /** Webhook resend: resent under a minute ago (HTTP 429, Retry-After). */
    public static final String RESEND_TOO_SOON = "RESEND_TOO_SOON";
    /** Static-deposit resend: no webhook was ever queued - the wallet had no callback_url. */
    public static final String NO_DELIVERIES = "NO_DELIVERIES";
}
