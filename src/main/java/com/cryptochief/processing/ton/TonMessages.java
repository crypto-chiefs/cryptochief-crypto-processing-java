package com.cryptochief.processing.ton;

import java.math.BigInteger;

/** Builders for the BoC bodies used by TON contract calls. */
public final class TonMessages {

    static final long OP_JETTON_TRANSFER = 0x0f8a7ea5L;
    static final long OP_NFT_TRANSFER = 0x5fcc3d14L;
    static final long OP_TEXT_COMMENT = 0x00000000L;

    private TonMessages() {}

    /** TEP-74 Jetton transfer body. */
    public static byte[] jettonTransferBody(
            long queryId,
            BigInteger amount,
            TonAddress destination,
            TonAddress responseDestination,
            Cell customPayload,
            BigInteger forwardTon,
            Cell forwardPayload) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("jetton amount must be non-negative");
        }
        CellBuilder b = new CellBuilder()
                .storeUInt(OP_JETTON_TRANSFER, 32)
                .storeUInt(queryId, 64)
                .storeCoins(amount)
                .storeAddress(destination)
                .storeAddress(responseDestination)
                .storeMaybeRef(customPayload)
                .storeCoins(forwardTon == null ? BigInteger.ZERO : forwardTon);
        if (forwardPayload != null) {
            b.storeBit(true).storeRef(forwardPayload);
        } else {
            b.storeBit(false);
        }
        return b.endCell().toBoc();
    }

    /** TEP-62 NFT transfer body. */
    public static byte[] nftTransferBody(
            long queryId,
            TonAddress newOwner,
            TonAddress responseDestination,
            Cell customPayload,
            BigInteger forwardTon,
            Cell forwardPayload) {
        CellBuilder b = new CellBuilder()
                .storeUInt(OP_NFT_TRANSFER, 32)
                .storeUInt(queryId, 64)
                .storeAddress(newOwner)
                .storeAddress(responseDestination)
                .storeMaybeRef(customPayload)
                .storeCoins(forwardTon == null ? BigInteger.ZERO : forwardTon);
        if (forwardPayload != null) {
            b.storeBit(true).storeRef(forwardPayload);
        } else {
            b.storeBit(false);
        }
        return b.endCell().toBoc();
    }

    /** Op-0 text comment cell. */
    public static Cell textCommentCell(String text) {
        return new CellBuilder()
                .storeUInt(OP_TEXT_COMMENT, 32)
                .storeStringSnake(text)
                .endCell();
    }

    public static byte[] textCommentBody(String text) {
        return textCommentCell(text).toBoc();
    }
}
