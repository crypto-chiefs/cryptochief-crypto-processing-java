package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.ConvertRequest;
import com.cryptochief.processing.models.ConvertResponse;
import com.cryptochief.processing.models.CryptoCurrencies;
import com.cryptochief.processing.models.FiatCurrency;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/** Fiat ↔ crypto rate quotes, and what can be quoted. */
public final class CurrenciesService {

    private final HttpTransport transport;

    public CurrenciesService(HttpTransport transport) {
        this.transport = transport;
    }

    public ConvertResponse fiatToCrypto(ConvertRequest request) {
        return transport.send("/v1/currencies/convert/fiat-crypto", request, ConvertResponse.class);
    }

    public ConvertResponse cryptoToFiat(ConvertRequest request) {
        return transport.send("/v1/currencies/convert/crypto-fiat", request, ConvertResponse.class);
    }

    /**
     * Every fiat currency the platform can price in - the codes {@link #fiatToCrypto} and a
     * pay-in's {@code currency} accept.
     *
     * <p>The answer is a bare JSON array, so there is no envelope to unwrap.
     *
     * <p>Never {@code null}. The service builds its answer from a nil slice, so "no
     * currencies" arrives as literal JSON {@code null} rather than {@code []}; this method
     * hands back an empty list for it, because a signature that promises a list has to keep
     * that promise.
     */
    public List<FiatCurrency> fiats() {
        return transport.sendList("/v1/currencies/fiats", Map.of(),
                new TypeReference<List<FiatCurrency>>() {});
    }

    /**
     * Every crypto ticker the platform has a rate for, quoted against USDT and grouped by
     * the exchange it came from.
     *
     * <p>Rate availability only: a ticker listed here is one the platform can price, which
     * does not mean the project can be paid in it. For that, use
     * {@link BlockchainService#contractsAvailable()}.
     *
     * <p>Never {@code null}, and neither is anything inside it. The service can answer with
     * literal JSON {@code null} for the whole body, or with {@code null} for {@code tickers}
     * and {@code by_exchange} inside it; all three arrive here as
     * {@link CryptoCurrencies#empty()} or as empty collections, so
     * {@code cryptos().tickers()} is always safe to iterate.
     */
    public CryptoCurrencies cryptos() {
        CryptoCurrencies decoded =
                transport.send("/v1/currencies/cryptos", Map.of(), CryptoCurrencies.class);
        return decoded == null ? CryptoCurrencies.empty() : decoded;
    }
}
