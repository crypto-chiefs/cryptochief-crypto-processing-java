package com.cryptochief.processing.services;

import com.cryptochief.processing.Options;
import com.cryptochief.processing.exceptions.ConfigurationException;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.AddressRequest;
import com.cryptochief.processing.models.GenerateWalletRequest;
import com.cryptochief.processing.models.ListWalletsResponse;
import com.cryptochief.processing.models.PayInHistoryResponse;
import com.cryptochief.processing.models.RebindMasterRequest;
import com.cryptochief.processing.models.SetCallbackUrlRequest;
import com.cryptochief.processing.models.SetLabelRequest;
import com.cryptochief.processing.models.Wallet;
import com.cryptochief.processing.models.WalletHistoryQuery;
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
     * Every pay-in that used one deposit address - the same order records as
     * {@link com.cryptochief.processing.services.PayInsService#history()}, narrowed to a
     * single wallet. A deposit wallet can serve several orders over its lifetime, and this
     * is the list of them.
     *
     * <p>The address is matched case-insensitively, so either spelling of an EVM address
     * works. Only orders belonging to the project are returned: an address the project does
     * not own yields an empty page rather than an error, so an empty result is not proof the
     * address does not exist.
     */
    public PayInHistoryResponse history(WalletHistoryQuery query) {
        return transport.send("/v1/wallets/history", query, PayInHistoryResponse.class);
    }

    /** As {@link #history(WalletHistoryQuery)}, first page, with no date filter. */
    public PayInHistoryResponse history(String address) {
        return history(WalletHistoryQuery.forAddress(address));
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

    /**
     * Set or replace the label of a wallet - the name you gave it, at most 255 characters,
     * stored and never interpreted. Longer answers 400 with {@code LABEL_TOO_LONG}.
     *
     * <p>Every wallet type, unlike {@link #setCallbackUrl(String, String)}: a master wallet
     * is named the same way a static one is. The name is yours alone and changes nothing
     * about where funds go.
     *
     * <p>An empty {@code label} clears the name rather than leaving it alone - see
     * {@link #clearLabel(String)}, which says so out loud. Null is read the same way,
     * because the endpoint always writes the value it is given and has no "leave it as it
     * is".
     */
    public Wallet setLabel(String address, String label) {
        return transport.send("/v1/wallets/label",
                new SetLabelRequest(address, label), Wallet.class);
    }

    /**
     * Take the name off a wallet: sends an empty {@code label}, which is how the endpoint
     * spells "clear it". The wallet then reads back with {@link Wallet#label()} null.
     */
    public Wallet clearLabel(String address) {
        return setLabel(address, "");
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
