package com.cryptochief.processing.ton;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** TON cell builder. */
public final class CellBuilder {

    private static final int MAX_BITS = 1023;

    private long bits = 0;
    private int bitLen = 0;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final List<Cell> refs = new ArrayList<>();

    public CellBuilder storeUInt(long value, int bits) {
        if (bits < 0 || bits > 64) {
            throw new IllegalArgumentException("storeUInt: bits must be in 0..64, got " + bits);
        }
        for (int i = bits - 1; i >= 0; i--) {
            storeBit(((value >>> i) & 1L) != 0);
        }
        return this;
    }

    public CellBuilder storeBit(boolean b) {
        bitLen++;
        bits <<= 1;
        if (b) bits |= 1L;
        if (bitLen % 8 == 0) {
            bytes.write((int) (bits & 0xFF));
            bits = 0;
        }
        return this;
    }

    public CellBuilder storeCoins(BigInteger amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("storeCoins: negative amount " + amount);
        }
        byte[] raw;
        if (amount.signum() == 0) {
            raw = new byte[0];
        } else {
            byte[] tmp = amount.toByteArray();
            raw = (tmp[0] == 0 && tmp.length > 1) ? java.util.Arrays.copyOfRange(tmp, 1, tmp.length) : tmp;
        }
        if (raw.length > 15) {
            throw new IllegalArgumentException("coins value too large: " + raw.length + " bytes");
        }
        storeUInt(raw.length, 4);
        for (byte b : raw) storeUInt(b & 0xFF, 8);
        return this;
    }

    public CellBuilder storeAddress(TonAddress address) {
        if (address == null) {
            storeUInt(0, 2);
            return this;
        }
        storeUInt(2, 2);
        storeBit(false);
        storeUInt(address.workchain() & 0xFF, 8);
        for (byte b : address.hash()) storeUInt(b & 0xFF, 8);
        return this;
    }

    public CellBuilder storeMaybeRef(Cell child) {
        if (child == null) {
            storeBit(false);
        } else {
            storeBit(true);
            storeRef(child);
        }
        return this;
    }

    public CellBuilder storeRef(Cell child) {
        if (refs.size() >= 4) {
            throw new IllegalStateException("cell can hold at most 4 refs");
        }
        refs.add(child);
        return this;
    }

    public CellBuilder storeStringSnake(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int idx = 0; idx < data.length; idx++) {
            if (bitLen + 8 > MAX_BITS) {
                CellBuilder tail = new CellBuilder();
                tail.storeStringSnake(new String(data, idx, data.length - idx, StandardCharsets.UTF_8));
                storeRef(tail.endCell());
                return this;
            }
            storeUInt(data[idx] & 0xFF, 8);
        }
        return this;
    }

    public Cell endCell() {
        if (bitLen % 8 != 0) {
            int pad = 8 - (bitLen % 8);
            long padded = bits << pad;
            bytes.write((int) (padded & 0xFF));
        }
        return new Cell(bytes.toByteArray(), bitLen, refs);
    }
}
