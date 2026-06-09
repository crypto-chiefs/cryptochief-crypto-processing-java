package com.cryptochief.processing.services;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.ConvertRequest;
import com.cryptochief.processing.models.ConvertResponse;

/** Fiat ↔ crypto rate quotes. */
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
}
