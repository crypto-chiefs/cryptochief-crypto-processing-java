package com.cryptochief.processing;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.services.BlockchainService;
import com.cryptochief.processing.services.CreditsService;
import com.cryptochief.processing.services.CurrenciesService;
import com.cryptochief.processing.services.PayInsService;
import com.cryptochief.processing.services.PayoutsService;
import com.cryptochief.processing.services.StaticDepositsService;
import com.cryptochief.processing.services.WebhooksService;
import com.cryptochief.processing.services.SweepsService;
import com.cryptochief.processing.services.TransactionsService;
import com.cryptochief.processing.services.WalletsService;
import com.cryptochief.processing.services.WithdrawalsService;
import com.cryptochief.processing.ton.TonRpcClient;

/** Entry point to the Crypto Chief processing API. */
public final class CryptoChiefClient implements AutoCloseable {

    private final Options options;
    private final HttpTransport transport;

    private final PayoutsService payouts;
    private final TransactionsService transactions;
    private final PayInsService payIns;
    private final WalletsService wallets;
    private final SweepsService sweeps;
    private final WithdrawalsService withdrawals;
    private final StaticDepositsService staticDeposits;
    private final BlockchainService blockchain;
    private final CurrenciesService currencies;
    private final CreditsService credits;
    private final WebhooksService webhooks;

    private volatile TonRpcClient tonRpc;

    public CryptoChiefClient(Options options) {
        this(options, new HttpTransport(options));
    }

    private CryptoChiefClient(Options options, HttpTransport transport) {
        this.options = options;
        this.transport = transport;
        this.payouts = new PayoutsService(transport);
        this.transactions = new TransactionsService(this, transport);
        this.payIns = new PayInsService(transport);
        this.wallets = new WalletsService(transport, options);
        this.sweeps = new SweepsService(transport);
        this.withdrawals = new WithdrawalsService(transport);
        this.staticDeposits = new StaticDepositsService(transport);
        this.blockchain = new BlockchainService(transport);
        this.currencies = new CurrenciesService(transport);
        this.credits = new CreditsService(transport);
        this.webhooks = new WebhooksService(transport);
    }

    public static CryptoChiefClient create(String merchantId, String apiKey) {
        return new CryptoChiefClient(Options.builder()
                .merchantId(merchantId)
                .apiKey(apiKey)
                .build());
    }

    /**
     * A client over the same HTTP connection pool that sends {@code Idempotency-Key} on every call it makes:
     *
     * <pre>{@code
     * client.withIdempotencyKey("payout-2026-09-16-0001").payouts().execute(request);
     * }</pre>
     *
     * The header is covered by the signature, so it has to be set before signing - one added by an OkHttp
     * interceptor is not signed and the request is refused with {@code INVALID_SIGNATURE}. The value must be
     * printable ASCII with no space at either edge, since the server trims spaces and tabs before signing;
     * {@code null} or an empty string gives back a client that sends no header.
     *
     * <p>The server keeps the value in the billing record of the call, up to 255 bytes. It does not
     * deduplicate payouts - {@code ExecutePayoutRequest.orderId()} does that. The returned client does not
     * own the HTTP client, so {@link #close()} on it is a no-op; close the client it came from.
     *
     * @throws IllegalArgumentException the key cannot be sent as it is
     */
    public CryptoChiefClient withIdempotencyKey(String key) {
        return new CryptoChiefClient(options, transport.withIdempotencyKey(key));
    }

    /**
     * A signed request to any route of any Crypto Chief API that takes these credentials, for one the SDK
     * has no method for. Same signing, retries, clock correction and error envelope as a service call.
     *
     * <pre>{@code
     * record Balance(String credits) {}
     * var balance = client.request("GET", "/v1/balance", null, Balance.class);
     * }</pre>
     *
     * {@code method} goes on the wire and into the string to sign with {@code a-z} upper-cased. {@code path}
     * starts with {@code "/"} and carries the route without the base URL; a query goes on it as
     * {@code "?a=1&b=2"} and is signed as written, while the path itself is signed percent-decoded - the form
     * the server reads. {@code body} is serialised with Jackson; {@code null} sends none, which is what a
     * {@code GET} takes.
     */
    public <T> T request(String method, String path, Object body, Class<T> responseType) {
        return transport.request(method, path, body, responseType);
    }

    /** {@link #request(String, String, Object, Class)}, handing back the raw response bytes. */
    public byte[] request(String method, String path, Object body) {
        return transport.requestRaw(method, path, body);
    }

    public Options options() { return options; }
    public String merchantId() { return options.merchantId(); }
    public String baseUrl() { return options.baseUrl(); }

    public PayoutsService payouts() { return payouts; }
    public TransactionsService transactions() { return transactions; }
    public PayInsService payIns() { return payIns; }
    public WalletsService wallets() { return wallets; }
    public SweepsService sweeps() { return sweeps; }
    public WithdrawalsService withdrawals() { return withdrawals; }
    public StaticDepositsService staticDeposits() { return staticDeposits; }
    public BlockchainService blockchain() { return blockchain; }
    public CurrenciesService currencies() { return currencies; }
    public CreditsService credits() { return credits; }
    public WebhooksService webhooks() { return webhooks; }

    public TonRpcClient tonRpc() {
        TonRpcClient local = tonRpc;
        if (local == null) {
            synchronized (this) {
                local = tonRpc;
                if (local == null) {
                    local = new TonRpcClient(
                            options.merchantId(),
                            options.tonRpcBaseUrl(),
                            transport.http(),
                            options.userAgent());
                    tonRpc = local;
                }
            }
        }
        return local;
    }

    @Override
    public void close() {
        if (!transport.ownsHttpClient()) return;
        transport.http().dispatcher().executorService().shutdown();
        transport.http().connectionPool().evictAll();
    }
}
