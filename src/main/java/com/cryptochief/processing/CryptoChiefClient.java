package com.cryptochief.processing;

import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.services.BlockchainService;
import com.cryptochief.processing.services.CurrenciesService;
import com.cryptochief.processing.services.PayInsService;
import com.cryptochief.processing.services.PayoutsService;
import com.cryptochief.processing.services.StaticDepositsService;
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

    private volatile TonRpcClient tonRpc;

    public CryptoChiefClient(Options options) {
        this.options = options;
        this.transport = new HttpTransport(options);
        this.payouts = new PayoutsService(transport);
        this.transactions = new TransactionsService(this, transport);
        this.payIns = new PayInsService(transport);
        this.wallets = new WalletsService(transport, options);
        this.sweeps = new SweepsService(transport);
        this.withdrawals = new WithdrawalsService(transport);
        this.staticDeposits = new StaticDepositsService(transport);
        this.blockchain = new BlockchainService(transport);
        this.currencies = new CurrenciesService(transport);
    }

    public static CryptoChiefClient create(String merchantId, String apiKey) {
        return new CryptoChiefClient(Options.builder()
                .merchantId(merchantId)
                .apiKey(apiKey)
                .build());
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
