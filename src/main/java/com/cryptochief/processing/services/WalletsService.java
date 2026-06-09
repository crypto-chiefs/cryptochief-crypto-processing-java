package com.cryptochief.processing.services;

import com.cryptochief.processing.Options;
import com.cryptochief.processing.exceptions.ConfigurationException;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.AddressRequest;
import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.ListWalletsResponse;
import com.cryptochief.processing.models.Wallet;
import com.cryptochief.processing.rsa.RsaDecrypt;

import java.util.Map;

/** Wallet management. */
public final class WalletsService {

    private final HttpTransport transport;
    private final Options options;

    public WalletsService(HttpTransport transport, Options options) {
        this.transport = transport;
        this.options = options;
    }

    public Wallet generate(GenerateWalletRequest request) {
        return transport.send("/v1/wallets/generate", request, Wallet.class);
    }

    public ListWalletsResponse list() {
        return transport.send("/v1/wallets/list", Map.of(), ListWalletsResponse.class);
    }

    public Wallet info(String address) {
        return transport.send("/v1/wallets/info", new AddressRequest(address), Wallet.class);
    }

    public Wallet freeze(String address) {
        return transport.send("/v1/wallets/freeze", new AddressRequest(address), Wallet.class);
    }

    /** Requires {@link Options#rsaPrivateKey()} to be set. */
    public String decryptPrivateKey(String encrypted) {
        if (options.rsaPrivateKey() == null) {
            throw new ConfigurationException(
                    "cryptochief: RSA private key not configured — set Options.rsaPrivateKey");
        }
        return RsaDecrypt.oaepSha256(options.rsaPrivateKey(), encrypted);
    }
}
