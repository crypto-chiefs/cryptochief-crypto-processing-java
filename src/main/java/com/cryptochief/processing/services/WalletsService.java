package com.cryptochief.processing.services;

import com.cryptochief.processing.Options;
import com.cryptochief.processing.exceptions.ConfigurationException;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.AddressRequest;
import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.ListWalletsResponse;
import com.cryptochief.processing.models.RebindMasterRequest;
import com.cryptochief.processing.models.SetCallbackUrlRequest;
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

    /**
     * Re-point a transit or static wallet at another master wallet of the same project.
     *
     * <p>No money moves. What changes is where the NEXT sweep settles - including sweeps
     * already queued and not yet sent, which will land on the new master. Anything already
     * swept stays where it landed, on the previous master, and getting it across is a
     * separate transfer.
     *
     * <p>Idempotent: a wallet already bound to that master answers 200, unchanged. Master
     * wallets themselves cannot be re-pointed, and the new master has to be the project's
     * own, of the same chain family, and not frozen.
     */
    public Wallet rebindMaster(String address, String masterWalletAddress) {
        return transport.send("/v1/wallets/rebind-master",
                new RebindMasterRequest(address, masterWalletAddress), Wallet.class);
    }

    /**
     * Set the deposit webhook of a static wallet after it was created.
     *
     * <p>Static wallets only - master and transit answer 400. A deposit that was already
     * announced is not announced again to the new URL; the change applies from here on.
     *
     * <p>An empty {@code callbackUrl} clears the webhook rather than leaving it alone -
     * see {@link #clearCallbackUrl(String)}, which says so out loud. Null is read the same
     * way, because the endpoint always writes the value it is given and has no "leave it
     * as it is".
     */
    public Wallet setCallbackUrl(String address, String callbackUrl) {
        return transport.send("/v1/wallets/callback-url",
                new SetCallbackUrlRequest(address, callbackUrl), Wallet.class);
    }

    /**
     * Stop announcing deposits on a static wallet: sends an empty {@code callback_url},
     * which is how the endpoint spells "clear it".
     */
    public Wallet clearCallbackUrl(String address) {
        return setCallbackUrl(address, "");
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
