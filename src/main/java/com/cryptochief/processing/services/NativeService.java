package com.cryptochief.processing.services;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.http.Json;
import com.cryptochief.processing.models.NativeBuyRequest;
import com.cryptochief.processing.models.NativeOrder;
import com.cryptochief.processing.models.NativeQuote;
import com.cryptochief.processing.models.NativeQuoteRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Buying the native coin of a network (TRX, ETH, BNB, SOL, TON, ...) out of the platform's
 * liquidity, billed to the same credits balance as everything else. The price is the coins plus
 * the platform's own transfer fee, at the current rate; {@code receiveAddress}
 * may be any address - the merchant pays the transfer.
 *
 * <p>{@link #buy(NativeBuyRequest)} is synchronous and its answer is always a
 * {@link NativeOrder}: {@code delivered} (HTTP 200) means the coins are sent and {@code txHash}
 * carries the transfer; {@code refused} (HTTP 502, or 402 when the credits balance did not cover
 * the order) means nothing was sent or charged and {@code errorCode} says why - retrying with a
 * NEW idempotency key is safe; {@code unresolved} (HTTP 409, {@code needsAttention} set) means
 * the transfer's outcome never arrived and the coins may already be sent - do NOT retry it,
 * follow the order with {@link #order(String)} instead. Only a failure with no order to report
 * (a 409 {@code QUOTE_EXPIRED} / {@code QUOTE_ALREADY_USED} envelope, a 502
 * {@code INSUFFICIENT_LIQUIDITY} envelope, a gateway error page, ...) throws
 * {@link ApiException}.
 */
public final class NativeService {

    private final HttpTransport transport;

    public NativeService(HttpTransport transport) {
        this.transport = transport;
    }

    /** A price with a deadline, free to request. */
    public NativeQuote quote(NativeQuoteRequest request) {
        return transport.send("/v1/native/quote", request, NativeQuote.class);
    }

    /**
     * Buy the coins. Requires an {@code Idempotency-Key} - it is what makes a retry after a
     * timeout safe instead of a double purchase - so call it on a keyed client:
     *
     * <pre>{@code
     * client.withIdempotencyKey("buy-2026-09-18-0001").nativeCoin().buy(request);
     * }</pre>
     *
     * The key comes back as {@link NativeOrder#idempotencyKey()} and is what
     * {@link #order(String)} looks the order up by. (The accessor is {@code nativeCoin()} because
     * {@code native} is a Java keyword.)
     *
     * <p>A refused or unresolved order arrives on a non-2xx status (502, 402 or 409) with the
     * order itself as the body - a business outcome, not a transport failure - and is returned,
     * not thrown; branch on {@link NativeOrder#status()} (or {@code settled}/{@code needsAttention})
     * rather than catching.
     *
     * @throws IllegalArgumentException the client sends no {@code Idempotency-Key}
     * @throws ApiException a failure with no order to report
     */
    public NativeOrder buy(NativeBuyRequest request) {
        if (transport.idempotencyKey() == null) {
            throw new IllegalArgumentException("cryptochief: native buy requires an Idempotency-Key:"
                    + " call client.withIdempotencyKey(key).nativeCoin().buy(...) - without the key"
                    + " a retry after a timeout would buy the coins twice, and the API refuses"
                    + " the call with 400 IDEMPOTENCY_KEY_REQUIRED");
        }
        try {
            return transport.send("/v1/native/buy", request, NativeOrder.class);
        } catch (ApiException e) {
            NativeOrder order = orderFromError(e);
            if (order != null) {
                return order;
            }
            throw e;
        }
    }

    /** The order whose {@code idempotency_key} is {@code key}; looked up inside the caller's own project. */
    public NativeOrder order(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("cryptochief: native order key is required");
        }
        return transport.send("/v1/native/order", Map.of("key", key), NativeOrder.class);
    }

    /**
     * A refused order answers 502 - or 402 when the reason is insufficient credits - and an
     * unresolved one 409, each with the order itself as the body: a business outcome, not a
     * transport failure. Recover it; anything else (an error envelope, a gateway error page)
     * is rethrown. The {@code id} + {@code status} guard is what keeps an error envelope from
     * being mistaken for an order.
     */
    private static NativeOrder orderFromError(ApiException err) {
        if (err.status() != 402 && err.status() != 409 && err.status() != 502) {
            return null;
        }
        String raw = err.raw();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        JsonNode node;
        try {
            node = Json.MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            return null;
        }
        if (node == null || !node.isObject() || !node.hasNonNull("id") || !node.hasNonNull("status")) {
            return null;
        }
        try {
            return Json.MAPPER.treeToValue(node, NativeOrder.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
