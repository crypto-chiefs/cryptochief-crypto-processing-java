package com.cryptochief.processing.evm;

/** Legacy Keccak-256 — the variant Ethereum uses (pre-NIST SHA-3). */
public final class Keccak256 {

    private static final int RATE_BYTES = 136;
    private static final long[] RC = {
            0x0000000000000001L, 0x0000000000008082L,
            0x800000000000808aL, 0x8000000080008000L,
            0x000000000000808bL, 0x0000000080000001L,
            0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008aL, 0x0000000000000088L,
            0x0000000080008009L, 0x000000008000000aL,
            0x000000008000808bL, 0x800000000000008bL,
            0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L,
            0x000000000000800aL, 0x800000008000000aL,
            0x8000000080008081L, 0x8000000000008080L,
            0x0000000080000001L, 0x8000000080008008L,
    };
    private static final int[] ROT = {
            0, 1, 62, 28, 27,
            36, 44, 6, 55, 20,
            3, 10, 43, 25, 39,
            41, 45, 15, 21, 8,
            18, 2, 61, 56, 14,
    };

    private Keccak256() {}

    public static byte[] hash(byte[] input) {
        long[] state = new long[25];
        int offset = 0;
        while (offset + RATE_BYTES <= input.length) {
            absorbBlock(state, input, offset);
            keccakF(state);
            offset += RATE_BYTES;
        }
        byte[] tail = new byte[RATE_BYTES];
        System.arraycopy(input, offset, tail, 0, input.length - offset);
        tail[input.length - offset] = 0x01;
        tail[RATE_BYTES - 1] = (byte) (tail[RATE_BYTES - 1] | 0x80);
        absorbBlock(state, tail, 0);
        keccakF(state);
        byte[] out = new byte[32];
        for (int i = 0; i < 4; i++) {
            long lane = state[i];
            for (int j = 0; j < 8; j++) {
                out[i * 8 + j] = (byte) ((lane >>> (8 * j)) & 0xFF);
            }
        }
        return out;
    }

    private static void absorbBlock(long[] state, byte[] data, int offset) {
        for (int i = 0; i < RATE_BYTES / 8; i++) {
            long lane = 0L;
            for (int j = 0; j < 8; j++) {
                lane |= ((long) (data[offset + i * 8 + j] & 0xFF)) << (8 * j);
            }
            state[i] ^= lane;
        }
    }

    private static void keccakF(long[] state) {
        long[] c = new long[5];
        long[] d = new long[5];
        long[] b = new long[25];
        for (int round = 0; round < 24; round++) {
            for (int x = 0; x < 5; x++) {
                c[x] = state[x] ^ state[x + 5] ^ state[x + 10] ^ state[x + 15] ^ state[x + 20];
            }
            for (int x = 0; x < 5; x++) {
                d[x] = c[(x + 4) % 5] ^ rotl(c[(x + 1) % 5], 1);
            }
            for (int i = 0; i < 25; i++) {
                state[i] ^= d[i % 5];
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    int idx = x + 5 * y;
                    int newX = y;
                    int newY = (2 * x + 3 * y) % 5;
                    b[newX + 5 * newY] = rotl(state[idx], ROT[idx]);
                }
            }
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    int idx = x + 5 * y;
                    state[idx] = b[idx] ^ ((~b[(x + 1) % 5 + 5 * y]) & b[(x + 2) % 5 + 5 * y]);
                }
            }
            state[0] ^= RC[round];
        }
    }

    private static long rotl(long v, int n) {
        int r = n & 0x3F;
        if (r == 0) return v;
        return (v << r) | (v >>> (64 - r));
    }
}
