package com.cryptochief.processing.ton;

import java.util.List;

/** Immutable TON cell. */
public final class Cell {

    private final byte[] data;
    private final int bitLength;
    private final List<Cell> refs;

    Cell(byte[] data, int bitLength, List<Cell> refs) {
        this.data = data;
        this.bitLength = bitLength;
        this.refs = List.copyOf(refs);
    }

    public byte[] data() {
        return data.clone();
    }

    public int bitLength() {
        return bitLength;
    }

    public List<Cell> refs() {
        return refs;
    }

    public byte[] toBoc() {
        return BocSerializer.serialize(this, false, true);
    }

    /** Package-private accessor without defensive copy, for serialiser. */
    byte[] dataNoCopy() {
        return data;
    }
}
