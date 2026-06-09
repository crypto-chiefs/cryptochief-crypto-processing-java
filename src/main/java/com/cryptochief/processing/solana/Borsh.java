package com.cryptochief.processing.solana;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/** Borsh-typed value for Anchor instruction encoding. */
public final class Borsh {

    private final Supplier<byte[]> encoder;

    private Borsh(Supplier<byte[]> encoder) {
        this.encoder = encoder;
    }

    public byte[] encode() {
        return encoder.get();
    }

    public static Borsh u8(int n) {
        if (n < 0 || n > 0xFF) throw new IllegalArgumentException("u8 out of range: " + n);
        return new Borsh(() -> new byte[]{(byte) n});
    }

    public static Borsh u16(int n) {
        if (n < 0 || n > 0xFFFF) throw new IllegalArgumentException("u16 out of range: " + n);
        return new Borsh(() ->
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) n).array());
    }

    public static Borsh u32(long n) {
        if (n < 0 || n > 0xFFFFFFFFL) throw new IllegalArgumentException("u32 out of range: " + n);
        return new Borsh(() ->
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) n).array());
    }

    public static Borsh u64(long n) {
        return new Borsh(() ->
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array());
    }

    public static Borsh u64(BigInteger n) {
        if (n.signum() < 0) throw new IllegalArgumentException("u64 negative: " + n);
        if (n.compareTo(BigInteger.ONE.shiftLeft(64)) >= 0) {
            throw new IllegalArgumentException("u64 overflow: " + n);
        }
        return new Borsh(() -> littleEndian(n, 8));
    }

    public static Borsh i8(int n) {
        return u8(n & 0xFF);
    }

    public static Borsh i16(int n) {
        return u16(n & 0xFFFF);
    }

    public static Borsh i32(int n) {
        return u32(Integer.toUnsignedLong(n));
    }

    public static Borsh i64(long n) {
        return u64(n);
    }

    public static Borsh u128(BigInteger n) {
        if (n.signum() < 0) throw new IllegalArgumentException("u128 negative: " + n);
        if (n.compareTo(BigInteger.ONE.shiftLeft(128)) >= 0) {
            throw new IllegalArgumentException("u128 overflow: " + n);
        }
        return new Borsh(() -> littleEndian(n, 16));
    }

    public static Borsh bool(boolean b) {
        return new Borsh(() -> new byte[]{(byte) (b ? 1 : 0)});
    }

    public static Borsh string(String s) {
        return new Borsh(() -> {
            byte[] data = s.getBytes(StandardCharsets.UTF_8);
            return ByteBuffer.allocate(4 + data.length)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(data.length)
                    .put(data)
                    .array();
        });
    }

    public static Borsh bytes(byte[] b) {
        return new Borsh(() -> ByteBuffer.allocate(4 + b.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(b.length)
                .put(b)
                .array());
    }

    /** Anchor {@code [u8; N]} — no length prefix. */
    public static Borsh fixedBytes(byte[] b, int n) {
        if (b.length != n) {
            throw new IllegalArgumentException("fixedBytes: expected " + n + " bytes, got " + b.length);
        }
        return new Borsh(b::clone);
    }

    /** Solana 32-byte pubkey from base58 string or raw bytes. */
    public static Borsh pubkey(Object value) {
        return new Borsh(() -> {
            byte[] raw;
            if (value instanceof String s) {
                raw = Base58.decode(s);
            } else if (value instanceof byte[] b) {
                raw = b;
            } else {
                throw new IllegalArgumentException(
                        "pubkey: want String or byte[], got " + (value == null ? "null" : value.getClass().getSimpleName()));
            }
            if (raw.length != 32) {
                throw new IllegalArgumentException("pubkey: want 32 bytes, got " + raw.length);
            }
            return raw.clone();
        });
    }

    public static Borsh option(Borsh inner) {
        return new Borsh(() -> {
            if (inner == null) return new byte[]{0};
            byte[] body = inner.encode();
            byte[] out = new byte[1 + body.length];
            out[0] = 1;
            System.arraycopy(body, 0, out, 1, body.length);
            return out;
        });
    }

    public static Borsh vec(List<Borsh> items) {
        return new Borsh(() -> {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(items.size()).array());
            for (Borsh it : items) buf.writeBytes(it.encode());
            return buf.toByteArray();
        });
    }

    public static Borsh struct(Borsh... fields) {
        return new Borsh(() -> {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            for (Borsh f : fields) buf.writeBytes(f.encode());
            return buf.toByteArray();
        });
    }

    private static byte[] littleEndian(BigInteger n, int length) {
        byte[] out = new byte[length];
        byte[] raw = n.toByteArray();
        int start = (raw.length > 0 && raw[0] == 0) ? 1 : 0;
        int copyLen = Math.min(raw.length - start, length);
        for (int i = 0; i < copyLen; i++) {
            out[i] = raw[raw.length - 1 - i];
        }
        return out;
    }
}
