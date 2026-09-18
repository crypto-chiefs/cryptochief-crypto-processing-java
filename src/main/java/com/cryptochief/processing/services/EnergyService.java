package com.cryptochief.processing.services;

import com.cryptochief.processing.exceptions.ApiException;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.http.Json;
import com.cryptochief.processing.models.EnergyOrder;
import com.cryptochief.processing.models.EnergyQuote;
import com.cryptochief.processing.models.EnergyQuoteRequest;
import com.cryptochief.processing.models.EnergyRentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * TRON energy rental, billed to the same credits balance as everything else.
 *
 * <p>{@link #rent(EnergyRentRequest)} is synchronous and its answer is always an
 * {@link EnergyOrder}: {@code delivered} (HTTP 200) means the energy is delegated;
 * {@code refused} (HTTP 502, or 402 when the credits balance did not cover the order) means
 * nothing was bought or charged and {@code errorCode} says why - retrying with a NEW idempotency
 * key is safe; {@code unresolved} (HTTP 409, {@code needsAttention} set) means the supplier's
 * answer never arrived and the energy may already be delegated - do NOT retry it, follow the
 * order with {@link #order(String)} instead. Only a failure with no order to report (a 409
 * {@code NOT_WORTH_RENTING} / {@code QUOTE_EXPIRED} envelope, a gateway error page, ...) throws
 * {@link ApiException}.
 */
public final class EnergyService {

    private final HttpTransport transport;

    public EnergyService(HttpTransport transport) {
        this.transport = transport;
    }

    /** A price with a deadline, free to request. */
    public EnergyQuote quote(EnergyQuoteRequest request) {
        return transport.send("/v1/energy/quote", request, EnergyQuote.class);
    }

    /**
     * Buy the energy. Requires an {@code Idempotency-Key} - it is what makes a retry after a
     * timeout safe instead of a double purchase - so call it on a keyed client:
     *
     * <pre>{@code
     * client.withIdempotencyKey("rent-2026-09-18-0001").energy().rent(request);
     * }</pre>
     *
     * The key comes back as {@link EnergyOrder#idempotencyKey()} and is what
     * {@link #order(String)} looks the order up by.
     *
     * <p>A refused or unresolved order arrives on a non-2xx status (502, 402 or 409) with the
     * order itself as the body - a business outcome, not a transport failure - and is returned,
     * not thrown; branch on {@link EnergyOrder#status()} (or {@code settled}/{@code needsAttention})
     * rather than catching.
     *
     * @throws IllegalArgumentException the client sends no {@code Idempotency-Key}
     * @throws ApiException a failure with no order to report
     */
    public EnergyOrder rent(EnergyRentRequest request) {
        if (transport.idempotencyKey() == null) {
            throw new IllegalArgumentException("cryptochief: energy rent requires an Idempotency-Key:"
                    + " call client.withIdempotencyKey(key).energy().rent(...) - without the key"
                    + " a retry after a timeout would buy the energy twice, and the API refuses"
                    + " the call with 400 IDEMPOTENCY_KEY_REQUIRED");
        }
        try {
            return transport.send("/v1/energy/rent", request, EnergyOrder.class);
        } catch (ApiException e) {
            EnergyOrder order = orderFromError(e);
            if (order != null) {
                return order;
            }
            throw e;
        }
    }

    /** The order whose {@code idempotency_key} is {@code key}; looked up inside the caller's own project. */
    public EnergyOrder order(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("cryptochief: energy order key is required");
        }
        return transport.send("/v1/energy/order", Map.of("key", key), EnergyOrder.class);
    }

    /**
     * A refused order answers 502 - or 402 when the reason is insufficient credits - and an
     * unresolved one 409, each with the order itself as the body: a business outcome, not a
     * transport failure. Recover it; anything else (an error envelope, a gateway error page)
     * is rethrown. The {@code id} + {@code status} guard is what keeps an error envelope from
     * being mistaken for an order.
     */
    private static EnergyOrder orderFromError(ApiException err) {
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
            return Json.MAPPER.treeToValue(node, EnergyOrder.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
