package com.cryptochief.processing.services;

import com.cryptochief.processing.Chain;
import com.cryptochief.processing.CryptoChiefClient;
import com.cryptochief.processing.evm.EvmAbi;
import com.cryptochief.processing.http.HttpTransport;
import com.cryptochief.processing.models.ContractCall;
import com.cryptochief.processing.models.ExecuteTransactionRequest;
import com.cryptochief.processing.models.HistoryQuery;
import com.cryptochief.processing.models.SignTransactionRequest;
import com.cryptochief.processing.models.SignTransactionResponse;
import com.cryptochief.processing.models.SolanaAccount;
import com.cryptochief.processing.models.TransactionHistoryResponse;
import com.cryptochief.processing.models.TransactionInfo;
import com.cryptochief.processing.models.TxType;
import com.cryptochief.processing.models.UuidRequest;
import com.cryptochief.processing.solana.Anchor;
import com.cryptochief.processing.solana.Borsh;
import com.cryptochief.processing.ton.TonAddress;
import com.cryptochief.processing.ton.TonMessages;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

/** Two-phase sign/execute plus high-level EVM, Solana, TON helpers. */
public final class TransactionsService {

    private final CryptoChiefClient client;
    private final HttpTransport transport;

    public TransactionsService(CryptoChiefClient client, HttpTransport transport) {
        this.client = client;
        this.transport = transport;
    }

    public SignTransactionResponse sign(SignTransactionRequest request) {
        return transport.send("/v1/transaction/signature", request, SignTransactionResponse.class);
    }

    public TransactionInfo execute(ExecuteTransactionRequest request) {
        return transport.send("/v1/transaction/execute", request, TransactionInfo.class);
    }

    public TransactionInfo execute(String uuid) {
        return execute(ExecuteTransactionRequest.of(uuid));
    }

    public TransactionInfo info(String uuid) {
        return transport.send("/v1/transaction/info", new UuidRequest(uuid), TransactionInfo.class);
    }

    public TransactionHistoryResponse history(HistoryQuery query) {
        return transport.send("/v1/transaction/history", query, TransactionHistoryResponse.class);
    }

    public TransactionHistoryResponse history() {
        return history(HistoryQuery.empty());
    }

    // ── EVM / TRON ─────────────────────────────────────────────────────────

    public SignTransactionResponse signEvmCall(
            Chain network, String fromAddress, String contract, String method,
            List<Object> args, String value, String urlCallback) {
        String data = EvmAbi.encodeCallHex(method, args == null ? new Object[0] : args.toArray());
        return sign(new SignTransactionRequest(
                network, fromAddress, TxType.CONTRACT, null, null, null,
                List.of(new ContractCall(contract, value == null ? "0" : value, data, null, null)),
                urlCallback));
    }

    public SignTransactionResponse signEvmCall(
            Chain network, String fromAddress, String contract, String method, List<Object> args) {
        return signEvmCall(network, fromAddress, contract, method, args, "0", null);
    }

    public SignTransactionResponse signTronCall(
            Chain network, String fromAddress, String contract, String method,
            List<Object> args, String value, String urlCallback) {
        return signEvmCall(network, fromAddress, contract, method, args, value, urlCallback);
    }

    public SignTransactionResponse erc20Transfer(
            Chain network, String fromAddress, String tokenContract,
            String recipient, BigInteger amount, String urlCallback) {
        return signEvmCall(network, fromAddress, tokenContract,
                "transfer(address,uint256)", List.of(recipient, amount), "0", urlCallback);
    }

    public SignTransactionResponse erc20Transfer(
            Chain network, String fromAddress, String tokenContract,
            String recipient, BigInteger amount) {
        return erc20Transfer(network, fromAddress, tokenContract, recipient, amount, null);
    }

    // ── Solana ─────────────────────────────────────────────────────────────

    public SignTransactionResponse signAnchorCall(
            Chain network, String fromAddress, String program, String method,
            List<Borsh> args, List<SolanaAccount> accounts, String urlCallback) {
        byte[] data = Anchor.encodeInstruction(method,
                args == null ? new Borsh[0] : args.toArray(new Borsh[0]));
        return sign(new SignTransactionRequest(
                network, fromAddress, TxType.CONTRACT, null, null, null,
                List.of(new ContractCall(program, null,
                        Base64.getEncoder().encodeToString(data), accounts, null)),
                urlCallback));
    }

    public SignTransactionResponse signSolanaCall(
            Chain network, String fromAddress, String program, byte[] instructionData,
            List<SolanaAccount> accounts, String urlCallback) {
        return sign(new SignTransactionRequest(
                network, fromAddress, TxType.CONTRACT, null, null, null,
                List.of(new ContractCall(program, null,
                        Base64.getEncoder().encodeToString(instructionData), accounts, null)),
                urlCallback));
    }

    // ── TON ────────────────────────────────────────────────────────────────

    public SignTransactionResponse signTonCall(
            Chain network, String fromAddress, String contract, byte[] bodyCell,
            String value, Boolean bounce, String urlCallback) {
        return sign(new SignTransactionRequest(
                network, fromAddress, TxType.CONTRACT, null, null, null,
                List.of(new ContractCall(contract,
                        value == null ? "0" : value,
                        Base64.getEncoder().encodeToString(bodyCell),
                        null, bounce)),
                urlCallback));
    }

    public SignTransactionResponse jettonTransfer(JettonTransferRequest req) {
        if (req.recipient() == null || req.recipient().isEmpty()) {
            throw new IllegalArgumentException("recipient is required");
        }
        if ((req.jettonMaster() == null || req.jettonMaster().isEmpty()) && req.jettonWalletAddress() == null) {
            throw new IllegalArgumentException("jettonMaster or jettonWalletAddress is required");
        }
        var rpc = client.tonRpc();
        String senderWallet = req.jettonWalletAddress() != null
                ? req.jettonWalletAddress()
                : rpc.lookupJettonWallet(req.jettonMaster(), req.fromAddress());

        TonAddress dest = TonAddress.parse(req.recipient());
        TonAddress respAddr = TonAddress.parse(
                req.responseDestination() != null ? req.responseDestination() : req.fromAddress());
        var forwardPayload = (req.memo() != null && !req.memo().isEmpty())
                ? TonMessages.textCommentCell(req.memo()) : null;
        BigInteger forwardAmount = req.forwardTonNanos() != null
                ? req.forwardTonNanos()
                : ((req.memo() == null || req.memo().isEmpty()) ? BigInteger.ZERO : BigInteger.ONE);

        BigInteger attached = req.attachedTonNanos();
        if (attached == null) {
            boolean recipientHasWallet = req.jettonMaster() != null
                    && !req.jettonMaster().isEmpty()
                    && rpc.hasJettonWallet(req.jettonMaster(), req.recipient());
            attached = recipientHasWallet ? BigInteger.valueOf(70_000_000L) : BigInteger.valueOf(150_000_000L);
        }
        byte[] body = TonMessages.jettonTransferBody(
                req.queryId(), req.amount(), dest, respAddr, null, forwardAmount, forwardPayload);
        return signTonCall(req.network(), req.fromAddress(), senderWallet, body,
                attached.toString(), true, req.urlCallback());
    }

    public SignTransactionResponse nftTransfer(NftTransferRequest req) {
        if (req.nftItem() == null || req.nftItem().isEmpty()) {
            throw new IllegalArgumentException("nftItem is required");
        }
        if (req.newOwner() == null || req.newOwner().isEmpty()) {
            throw new IllegalArgumentException("newOwner is required");
        }
        TonAddress owner = TonAddress.parse(req.newOwner());
        TonAddress respAddr = TonAddress.parse(
                req.responseDestination() != null ? req.responseDestination() : req.fromAddress());
        byte[] body = TonMessages.nftTransferBody(
                req.queryId(), owner, respAddr, null, req.forwardTonNanos(), null);
        BigInteger attached = req.attachedTonNanos() != null
                ? req.attachedTonNanos() : BigInteger.valueOf(50_000_000L);
        return signTonCall(req.network(), req.fromAddress(), req.nftItem(), body,
                attached.toString(), true, req.urlCallback());
    }

    public SignTransactionResponse sendTonComment(
            Chain network, String fromAddress, String recipient, String text,
            BigInteger amountTonNanos, String urlCallback) {
        if (recipient == null || recipient.isEmpty()) {
            throw new IllegalArgumentException("recipient is required");
        }
        byte[] body = TonMessages.textCommentBody(text);
        BigInteger amount = amountTonNanos == null ? BigInteger.ZERO : amountTonNanos;
        return signTonCall(network, fromAddress, recipient, body, amount.toString(), false, urlCallback);
    }

    /** Parameters for {@link #jettonTransfer(JettonTransferRequest)}. */
    public record JettonTransferRequest(
            Chain network,
            String fromAddress,
            String jettonMaster,
            String recipient,
            BigInteger amount,
            String jettonWalletAddress,
            String responseDestination,
            BigInteger attachedTonNanos,
            BigInteger forwardTonNanos,
            String memo,
            long queryId,
            String urlCallback
    ) {
        public static JettonTransferRequest simple(Chain network, String fromAddress,
                                                    String jettonMaster, String recipient, BigInteger amount) {
            return new JettonTransferRequest(network, fromAddress, jettonMaster, recipient, amount,
                    null, null, null, null, null, 0L, null);
        }
    }

    /** Parameters for {@link #nftTransfer(NftTransferRequest)}. */
    public record NftTransferRequest(
            Chain network,
            String fromAddress,
            String nftItem,
            String newOwner,
            String responseDestination,
            BigInteger attachedTonNanos,
            BigInteger forwardTonNanos,
            long queryId,
            String urlCallback
    ) {
        public static NftTransferRequest simple(Chain network, String fromAddress,
                                                 String nftItem, String newOwner) {
            return new NftTransferRequest(network, fromAddress, nftItem, newOwner,
                    null, null, null, 0L, null);
        }
    }
}
