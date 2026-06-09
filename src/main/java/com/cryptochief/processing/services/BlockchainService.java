package com.cryptochief.processing.services;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.AvailableContractsResponse;
import com.cryptochief.processing.models.NetworkRequest;
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

    public List<WalletBalanceRow> walletBalance(Chain chain, List<String> addresses) {
        return walletBalance(chain, addresses, List.of());
    }

    public List<WalletBalanceRow> walletBalance(Chain chain, List<String> addresses, List<String> contracts) {
        WalletBalanceRequest body = new WalletBalanceRequest(chain, addresses,
                (contracts == null || contracts.isEmpty()) ? null : contracts);
        return transport.send("/v1/blockchain/wallet/balance", body,
                new TypeReference<List<WalletBalanceRow>>() {});
    }

    public List<TxStatusRow> transactionStatus(Chain chain, String hash) {
        return transport.send("/v1/blockchain/transaction/status",
                new TransactionStatusRequest(chain, hash),
                new TypeReference<List<TxStatusRow>>() {});
    }
}
