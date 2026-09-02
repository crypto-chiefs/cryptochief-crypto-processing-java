package com.cryptochief.processing.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every crypto ticker the platform has a rate for, quoted against {@code quote} (USDT).
 *
 * <p>{@code tickers} is the union across exchanges and {@code byExchange} says which
 * exchange carries which, keyed by exchange name ({@code binance}, {@code bybit},
 * {@code exmo}, {@code kucoin}, ...). {@code count} is the size of the union as the platform
 * counted it.
 *
 * <p>{@link #tickers()} and {@link #byExchange()} are never {@code null}, and neither is a
 * list inside the map. The service builds them from nil slices and maps, which marshal as
 * JSON {@code null} rather than {@code []} / <code>{}</code> when empty, so the decode
 * normalizes them: nothing to report reads as an empty collection. {@code quote} is
 * {@code null} on an answer that carries nothing to quote against.
 *
 * <p>This is rate availability, not the asset catalogue: a ticker listed here is one the
 * platform can price, which does not mean the project can be paid in it. For that, use
 * {@code client.blockchain().contractsAvailable()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CryptoCurrencies(
        @JsonProperty("tickers") List<String> tickers,
        @JsonProperty("by_exchange") Map<String, List<String>> byExchange,
        @JsonProperty("quote") String quote,
        @JsonProperty("count") int count
) {

    public CryptoCurrencies {
        tickers = tickers == null ? List.of() : tickers;
        byExchange = normalizeByExchange(byExchange);
    }

    /**
     * The answer for a body of literal JSON {@code null} - empty collections, no quote,
     * {@code count} of zero. Nothing was reported; nothing is missing.
     */
    public static CryptoCurrencies empty() {
        return new CryptoCurrencies(null, null, null, 0);
    }

    private static Map<String, List<String>> normalizeByExchange(Map<String, List<String>> src) {
        if (src == null || src.isEmpty()) {
            return Map.of();
        }
        // An exchange the platform knows but has no tickers for arrives with a null list,
        // for the same reason the whole map can: a nil slice on the other side. Map.copyOf
        // would reject those outright, so replace them rather than drop the key - the key
        // is the platform saying it knows that exchange.
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : src.entrySet()) {
            normalized.put(entry.getKey(),
                    entry.getValue() == null ? List.of() : entry.getValue());
        }
        return Collections.unmodifiableMap(normalized);
    }
}
