package com.cryptochief.processing.evm;

import com.cryptochief.processing.tron.TronAddress;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Solidity-style ABI encoder for EVM / TRON contract calls. */
public final class EvmAbi {

    private EvmAbi() {}

    public static byte[] encodeCall(String signature, Object... args) {
        ParsedSignature parsed = parseSignature(signature);
        if (parsed.types.size() != args.length) {
            throw new IllegalArgumentException(
                    "signature has " + parsed.types.size() + " args, got " + args.length);
        }
        byte[] selector = selector(signature);
        byte[] body = encodeTuple(parsed.types, Arrays.asList(args));
        byte[] out = new byte[selector.length + body.length];
        System.arraycopy(selector, 0, out, 0, selector.length);
        System.arraycopy(body, 0, out, selector.length, body.length);
        return out;
    }

    public static String encodeCallHex(String signature, Object... args) {
        return "0x" + HexUtil.toHex(encodeCall(signature, args));
    }

    public static byte[] selector(String signature) {
        String canonical = canonicalSignature(signature);
        byte[] hash = Keccak256.hash(canonical.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOfRange(hash, 0, 4);
    }

    private sealed interface AbiType permits UIntT, SIntT, AddressT, BoolT, FixedBytesT, DynamicBytesT, StringT, ArrayT {
        boolean dynamic();
    }

    private record UIntT(int bits) implements AbiType { public boolean dynamic() { return false; } }
    private record SIntT(int bits) implements AbiType { public boolean dynamic() { return false; } }
    private record AddressT() implements AbiType { public boolean dynamic() { return false; } }
    private record BoolT() implements AbiType { public boolean dynamic() { return false; } }
    private record FixedBytesT(int length) implements AbiType { public boolean dynamic() { return false; } }
    private record DynamicBytesT() implements AbiType { public boolean dynamic() { return true; } }
    private record StringT() implements AbiType { public boolean dynamic() { return true; } }
    private record ArrayT(AbiType element, int size) implements AbiType {
        public boolean dynamic() { return size < 0 || element.dynamic(); }
    }

    private record ParsedSignature(String name, List<AbiType> types) {}

    private static ParsedSignature parseSignature(String sig) {
        int open = sig.indexOf('(');
        int close = sig.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("bad signature: \"" + sig + "\"");
        }
        String name = sig.substring(0, open).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("signature missing name: \"" + sig + "\"");
        }
        String body = sig.substring(open + 1, close).trim();
        if (body.isEmpty()) {
            return new ParsedSignature(name, List.of());
        }
        List<AbiType> types = new ArrayList<>();
        for (String part : body.split(",")) {
            String t = part.trim();
            int space = t.indexOf(' ');
            if (space >= 0) t = t.substring(0, space).trim();
            types.add(parseType(expandAlias(t)));
        }
        return new ParsedSignature(name, types);
    }

    private static AbiType parseType(String spec) {
        String s = spec.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty type");
        if (s.endsWith("]")) {
            int open = s.lastIndexOf('[');
            if (open <= 0) throw new IllegalArgumentException("malformed type: \"" + s + "\"");
            AbiType inner = parseType(s.substring(0, open));
            String span = s.substring(open + 1, s.length() - 1);
            int size = span.isEmpty() ? -1 : Integer.parseInt(span);
            if (size < -1) throw new IllegalArgumentException("negative array size");
            return new ArrayT(inner, size);
        }
        return switch (s) {
            case "address" -> new AddressT();
            case "bool" -> new BoolT();
            case "string" -> new StringT();
            case "bytes" -> new DynamicBytesT();
            default -> {
                if (s.startsWith("uint")) yield new UIntT(intBits(s.substring(4), "uint"));
                if (s.startsWith("int")) yield new SIntT(intBits(s.substring(3), "int"));
                if (s.startsWith("bytes")) {
                    int n = Integer.parseInt(s.substring(5));
                    if (n < 1 || n > 32) {
                        throw new IllegalArgumentException("invalid fixed bytes width: " + n);
                    }
                    yield new FixedBytesT(n);
                }
                throw new IllegalArgumentException("unsupported type \"" + s + "\"");
            }
        };
    }

    private static int intBits(String raw, String kind) {
        if (raw.isEmpty()) return 256;
        int bits;
        try {
            bits = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + kind + " width \"" + raw + "\"", e);
        }
        if (bits < 8 || bits > 256 || bits % 8 != 0) {
            throw new IllegalArgumentException("invalid " + kind + " width " + bits);
        }
        return bits;
    }

    private static String expandAlias(String t) {
        if (t.endsWith("]")) {
            int open = t.lastIndexOf('[');
            return expandAlias(t.substring(0, open)) + t.substring(open);
        }
        return switch (t) {
            case "uint" -> "uint256";
            case "int" -> "int256";
            case "byte" -> "bytes1";
            default -> t;
        };
    }

    private static String canonicalSignature(String sig) {
        ParsedSignature parsed = parseSignature(sig);
        StringBuilder sb = new StringBuilder(parsed.name).append('(');
        for (int i = 0; i < parsed.types.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(renderType(parsed.types.get(i)));
        }
        return sb.append(')').toString();
    }

    private static String renderType(AbiType t) {
        if (t instanceof UIntT u) return "uint" + u.bits;
        if (t instanceof SIntT s) return "int" + s.bits;
        if (t instanceof AddressT) return "address";
        if (t instanceof BoolT) return "bool";
        if (t instanceof FixedBytesT fb) return "bytes" + fb.length;
        if (t instanceof DynamicBytesT) return "bytes";
        if (t instanceof StringT) return "string";
        if (t instanceof ArrayT a) return renderType(a.element) + (a.size < 0 ? "[]" : "[" + a.size + "]");
        throw new IllegalStateException("unknown ABI type: " + t);
    }

    private static byte[] encodeTuple(List<AbiType> types, List<Object> values) {
        List<byte[]> tails = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            tails.add(encodeOne(types.get(i), values.get(i)));
        }
        int headSize = 32 * types.size();
        int[] offsets = new int[types.size()];
        int cursor = headSize;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).dynamic()) {
                offsets[i] = cursor;
                cursor += tails.get(i).length;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(cursor);
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).dynamic()) {
                writeAll(out, uint256Bytes(BigInteger.valueOf(offsets[i])));
            } else {
                writeAll(out, tails.get(i));
            }
        }
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i).dynamic()) {
                writeAll(out, tails.get(i));
            }
        }
        return out.toByteArray();
    }

    private static byte[] encodeOne(AbiType type, Object value) {
        if (type instanceof UIntT u) {
            return uint256Bytes(toBigUint(value, u.bits));
        }
        if (type instanceof SIntT) {
            return int256Bytes(toBigInt(value));
        }
        if (type instanceof AddressT) {
            if (!(value instanceof String addrString)) {
                throw new IllegalArgumentException("address: want String, got " + typeName(value));
            }
            byte[] addr = normaliseEvmAddress(addrString);
            byte[] padded = new byte[32];
            System.arraycopy(addr, 0, padded, 12, 20);
            return padded;
        }
        if (type instanceof BoolT) {
            if (!(value instanceof Boolean bv)) {
                throw new IllegalArgumentException("bool: want Boolean, got " + typeName(value));
            }
            byte[] padded = new byte[32];
            if (bv) padded[31] = 1;
            return padded;
        }
        if (type instanceof FixedBytesT fb) {
            byte[] raw = toBytes(value);
            if (raw.length != fb.length) {
                throw new IllegalArgumentException(
                        "bytes" + fb.length + ": expected " + fb.length + " bytes, got " + raw.length);
            }
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            return padded;
        }
        if (type instanceof DynamicBytesT) {
            return encodeDynBytes(toBytes(value));
        }
        if (type instanceof StringT) {
            if (!(value instanceof String sv)) {
                throw new IllegalArgumentException("string: want String, got " + typeName(value));
            }
            return encodeDynBytes(sv.getBytes(StandardCharsets.UTF_8));
        }
        if (type instanceof ArrayT arr) {
            List<?> items = toList(value);
            if (arr.size >= 0 && items.size() != arr.size) {
                throw new IllegalArgumentException(
                        "fixed array T[" + arr.size + "]: expected " + arr.size + " items, got " + items.size());
            }
            List<AbiType> inner = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) inner.add(arr.element);
            byte[] body = encodeTuple(inner, new ArrayList<>(items));
            if (arr.size < 0) {
                byte[] length = uint256Bytes(BigInteger.valueOf(items.size()));
                byte[] combined = new byte[length.length + body.length];
                System.arraycopy(length, 0, combined, 0, length.length);
                System.arraycopy(body, 0, combined, length.length, body.length);
                return combined;
            }
            return body;
        }
        throw new IllegalStateException("unknown ABI type: " + type);
    }

    private static byte[] encodeDynBytes(byte[] b) {
        int padLen = (32 - (b.length % 32)) % 32;
        byte[] out = new byte[32 + b.length + padLen];
        byte[] length = uint256Bytes(BigInteger.valueOf(b.length));
        System.arraycopy(length, 0, out, 0, 32);
        System.arraycopy(b, 0, out, 32, b.length);
        return out;
    }

    private static byte[] uint256Bytes(BigInteger n) {
        byte[] out = new byte[32];
        if (n.signum() == 0) return out;
        BigInteger v = n.signum() < 0 ? n.add(BigInteger.ONE.shiftLeft(256)) : n;
        byte[] raw = v.toByteArray();
        byte[] src = raw.length > 32 ? Arrays.copyOfRange(raw, raw.length - 32, raw.length) : raw;
        System.arraycopy(src, 0, out, 32 - src.length, src.length);
        return out;
    }

    private static byte[] int256Bytes(BigInteger n) {
        return n.signum() >= 0 ? uint256Bytes(n) : uint256Bytes(n.add(BigInteger.ONE.shiftLeft(256)));
    }

    private static BigInteger toBigUint(Object v, int bits) {
        BigInteger n = toBigInt(v);
        if (n.signum() < 0) {
            throw new IllegalArgumentException("uint" + bits + ": negative value " + n);
        }
        BigInteger max = BigInteger.ONE.shiftLeft(bits);
        if (n.compareTo(max) >= 0) {
            throw new IllegalArgumentException("uint" + bits + ": value " + n + " exceeds max");
        }
        return n;
    }

    private static BigInteger toBigInt(Object v) {
        if (v instanceof BigInteger bi) return bi;
        if (v instanceof Integer i) return BigInteger.valueOf(i);
        if (v instanceof Long l) return BigInteger.valueOf(l);
        if (v instanceof Short s) return BigInteger.valueOf(s);
        if (v instanceof Byte b) return BigInteger.valueOf(b);
        if (v instanceof String s) {
            String trimmed = s.strip();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("empty integer string");
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return new BigInteger(trimmed.substring(2), 16);
            }
            return new BigInteger(trimmed, 10);
        }
        throw new IllegalArgumentException("integer: unsupported type " + typeName(v));
    }

    private static byte[] toBytes(Object v) {
        if (v instanceof byte[] b) return b;
        if (v instanceof String s) {
            String trimmed = s.strip();
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return HexUtil.fromHex(trimmed.substring(2));
            }
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("bytes: unsupported type " + typeName(v));
    }

    private static List<?> toList(Object v) {
        if (v instanceof List<?> list) return list;
        if (v instanceof Object[] arr) return Arrays.asList(arr);
        throw new IllegalArgumentException("array: unsupported type " + typeName(v));
    }

    private static String typeName(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    private static void writeAll(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    /** Accepts {@code 0x…} hex, TRON {@code 0x41}-prefixed hex, or TRON {@code T…} base58. Returns 20 bytes. */
    public static byte[] normaliseEvmAddress(String input) {
        String s = input.strip();
        if (s.isEmpty()) throw new IllegalArgumentException("address: empty");
        if (s.length() >= 30 && (s.charAt(0) == 'T' || s.charAt(0) == 't')
                && !s.startsWith("0x") && !s.startsWith("0X")) {
            String hex = TronAddress.toHex(s);
            byte[] raw = HexUtil.fromHex(hex.startsWith("0x") ? hex.substring(2) : hex);
            if (raw.length == 21 && raw[0] == 0x41) {
                return Arrays.copyOfRange(raw, 1, 21);
            }
            if (raw.length == 20) return raw;
            throw new IllegalArgumentException("address: unexpected TRON length " + raw.length);
        }
        String trimmed = s;
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) trimmed = trimmed.substring(2);
        if (trimmed.length() == 42 && trimmed.startsWith("41")) trimmed = trimmed.substring(2);
        if (trimmed.length() != 40) {
            throw new IllegalArgumentException(
                    "address: want 20 hex bytes, got " + trimmed.length() + " chars");
        }
        return HexUtil.fromHex(trimmed);
    }
}
