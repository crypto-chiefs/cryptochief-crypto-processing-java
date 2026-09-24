package com.cryptochief.processing.models;

import com.cryptochief.processing.Chain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The answer of {@code POST /v1/transaction/signature}.
 *
 * <p>{@code supersededUuids} (EVM) lists the earlier unexecuted signatures from the same address
 * that this one replaced; they turn {@link TxStatus#CANCELLED}. Empty when there were none.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SignTransactionResponse(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("status") String status,
        @JsonProperty("signed_tx_hex") String signedTxHex,
        @JsonProperty("tx_hash") String txHash,
        @JsonProperty("expires_at") String expiresAt,
        @JsonProperty("chain_family") String chainFamily,
        @JsonProperty("network") Chain network,
        @JsonProperty("superseded_uuids") List<String> supersededUuids
) {
    public SignTransactionResponse {
        supersededUuids = supersededUuids == null ? List.of() : List.copyOf(supersededUuids);
    }

    /** The 0.12.0 constructor; {@code supersededUuids} is empty. */
    public SignTransactionResponse(String uuid, String status, String signedTxHex, String txHash,
                                   String expiresAt, String chainFamily, Chain network) {
        this(uuid, status, signedTxHex, txHash, expiresAt, chainFamily, network, List.of());
    }
}
