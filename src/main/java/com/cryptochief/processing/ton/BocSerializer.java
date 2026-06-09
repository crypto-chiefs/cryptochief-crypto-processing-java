package com.cryptochief.processing.ton;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

/** BoC v1 serializer (single-root). */
final class BocSerializer {

    private static final int BOC_MAGIC = 0xB5EE9C72;

    private BocSerializer() {}

    static byte[] serialize(Cell root, boolean hasIdx, boolean hasCrc32c) {
        List<Cell> flat = new ArrayList<>();
        Map<Cell, Integer> indexMap = new IdentityHashMap<>();
        visit(root, flat, indexMap);

        List<Cell> order = new ArrayList<>(flat);
        Collections.reverse(order);
        Map<Cell, Integer> orderMap = new IdentityHashMap<>();
        for (int i = 0; i < order.size(); i++) orderMap.put(order.get(i), i);

        int cellsCount = order.size();
        int cellSizeBytes = bytesNeeded(cellsCount);
        List<byte[]> cellBlobs = new ArrayList<>(cellsCount);
        for (Cell c : order) cellBlobs.add(encodeCell(c, orderMap, cellSizeBytes));
        int totalCellBytes = cellBlobs.stream().mapToInt(b -> b.length).sum();
        int offsetSizeBytes = bytesNeeded(totalCellBytes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, BOC_MAGIC);
        int flags = 0;
        if (hasIdx) flags |= 0x80;
        if (hasCrc32c) flags |= 0x40;
        flags |= (cellSizeBytes & 0x07);
        out.write(flags);
        out.write(offsetSizeBytes);
        writeUint(out, cellsCount, cellSizeBytes);
        writeUint(out, 1, cellSizeBytes);
        writeUint(out, 0, cellSizeBytes);
        writeUint(out, totalCellBytes, offsetSizeBytes);
        writeUint(out, 0, cellSizeBytes);
        if (hasIdx) {
            int cursor = 0;
            for (byte[] blob : cellBlobs) {
                cursor += blob.length;
                writeUint(out, cursor, offsetSizeBytes);
            }
        }
        for (byte[] blob : cellBlobs) out.writeBytes(blob);
        if (hasCrc32c) {
            CRC32C crc = new CRC32C();
            byte[] sofar = out.toByteArray();
            crc.update(sofar, 0, sofar.length);
            int v = (int) crc.getValue();
            out.write(v & 0xFF);
            out.write((v >>> 8) & 0xFF);
            out.write((v >>> 16) & 0xFF);
            out.write((v >>> 24) & 0xFF);
        }
        return out.toByteArray();
    }

    private static void visit(Cell c, List<Cell> flat, Map<Cell, Integer> indexMap) {
        if (indexMap.containsKey(c)) return;
        for (Cell r : c.refs()) visit(r, flat, indexMap);
        indexMap.put(c, flat.size());
        flat.add(c);
    }

    private static byte[] encodeCell(Cell c, Map<Cell, Integer> orderMap, int cellSizeBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int refsCount = c.refs().size();
        int d1 = refsCount;
        int fullBytes = c.bitLength() / 8;
        int partial = c.bitLength() % 8;
        int dataLenBytes = fullBytes + (partial > 0 ? 1 : 0);
        int d2 = fullBytes + dataLenBytes;
        out.write(d1);
        out.write(d2);
        byte[] data = c.dataNoCopy();
        if (partial == 0) {
            out.write(data, 0, dataLenBytes);
        } else {
            if (fullBytes > 0) out.write(data, 0, fullBytes);
            int lastByteIdx = dataLenBytes - 1;
            int raw = data[lastByteIdx] & 0xFF;
            int completion = 1 << (8 - partial - 1);
            int mask = (0xFF << (8 - partial)) & 0xFF;
            out.write((raw & mask) | completion);
        }
        for (Cell r : c.refs()) {
            writeUint(out, orderMap.get(r), cellSizeBytes);
        }
        return out.toByteArray();
    }

    private static void writeUint(ByteArrayOutputStream out, long value, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) {
            out.write((int) ((value >>> (8 * i)) & 0xFF));
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static int bytesNeeded(long value) {
        if (value == 0) return 1;
        long v = value;
        int n = 0;
        while (v > 0) {
            v >>>= 8;
            n++;
        }
        return n;
    }
}
