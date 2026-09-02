package com.cryptochief.processing.services;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.AvailableContractsResponse;
import com.cryptochief.processing.models.NetworkRequest;
import com.cryptochief.processing.models.SupportedBlockchain;
import com.cryptochief.processing.models.TransactionStatusRequest;
import com.cryptochief.processing.models.TxStatusRow;
import com.cryptochief.processing.models.WalletBalanceRequest;
import com.cryptochief.processing.models.WalletBalanceRow;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/** Read-only blockchain queries. */
public final class BlockchainService {

    private final HttpTransport transport;

    public BlockchainService(HttpTransport transport) {
        this.transport = transport;
    }

    public AvailableContractsResponse contractsAvailable() {
        return transport.send("/v1/blockchain/contracts/available", Map.of(),
                AvailableContractsResponse.class);
    }

    public AvailableContractsResponse contractsAvailable(Chain network) {
        if (network == null) return contractsAvailable();
        return transport.send("/v1/blockchain/contracts/available",
                new NetworkRequest(network), AvailableContractsResponse.class);
    }

    /**
     * Every coin and token the platform supports, on every network, regardless of what the
     * project has enabled - the catalogue behind a "which assets could we turn on" picker.
     *
     * <p>Same item shape as {@link #contractsAvailable()}, which is the list that actually
     * governs orders, sweeps and payouts. An asset here is not one the project can be paid
     * in yet.
     */
    public AvailableContractsResponse contractsList() {
        return transport.send("/v1/blockchain/contracts/list", Map.of(),
                AvailableContractsResponse.class);
    }

    /**
     * The chains the platform's blockchain scanner is currently connected to - which chains
     * it can read blocks from right now.
     *
     * <p>Infrastructure, not the project's asset catalogue: use {@link #contractsAvailable()}
     * for what the project can be paid in. The answer is a bare JSON array, so there is no
     * envelope to unwrap.
     *
     * <p>Never {@code null}. The service builds its answer from a nil slice, so "no chains"
     * arrives as literal JSON {@code null} rather than {@code []}; this method hands back an
     * empty list for it, because a signature that promises a list has to keep that promise.
     */
    public List<SupportedBlockchain> blockchains() {
        return transport.sendList("/v1/blockchains/list", Map.of(),
                new TypeReference<List<SupportedBlockchain>>() {});
    }

    public List<WalletBalanceRow> walletBalance(Chain chain, List<String> addresses) {
        return walletBalance(chain, addresses, List.of());
    }

    public List<WalletBalanceRow> walletBalance(Chain chain, List<String> addresses, List<String> contracts) {
        WalletBalanceRequest body = new WalletBalanceRequest(chain, addresses,
                (contracts == null || contracts.isEmpty()) ? null : contracts);
        return transport.sendList("/v1/blockchain/wallet/balance", body,
                new TypeReference<List<WalletBalanceRow>>() {});
    }

    public List<TxStatusRow> transactionStatus(Chain chain, String hash) {
        return transport.sendList("/v1/blockchain/transaction/status",
                new TransactionStatusRequest(chain, hash),
                new TypeReference<List<TxStatusRow>>() {});
    }
}
