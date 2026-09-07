package io.micronaut.serde.protobuf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds payloads by hand, including the malformed ones a real encoder would never produce.
 */
final class WireBytes {

    private WireBytes() {
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    static byte[] of(int... unsignedBytes) {
        byte[] result = new byte[unsignedBytes.length];
        for (int i = 0; i < unsignedBytes.length; i++) {
            result[i] = (byte) unsignedBytes[i];
        }
        return result;
    }

    static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.write((int) ((remaining & 0x7F) | 0x80));
            remaining >>>= 7;
        }
        out.write((int) remaining);
        return out.toByteArray();
    }

    /**
     * A varint carrying {@code value} but padded out to {@code length} bytes with redundant
     * continuation bytes. Legal for values, rejected for tags.
     */
    static byte[] overlongVarint(long value, int length) {
        byte[] result = new byte[length];
        long remaining = value;
        for (int i = 0; i < length - 1; i++) {
            result[i] = (byte) ((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        result[length - 1] = (byte) remaining;
        return result;
    }

    static byte[] tag(int fieldNumber, int wireType) {
        return varint((long) fieldNumber << 3 | wireType);
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A length-delimited field: tag, length, payload.
     */
    static byte[] delimited(int fieldNumber, byte[] payload) {
        return concat(tag(fieldNumber, 2), varint(payload.length), payload);
    }

    static byte[] fixed32(int value) {
        return of(value, value >>> 8, value >>> 16, value >>> 24);
    }

    static byte[] fixed64(long value) {
        byte[] result = new byte[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (byte) (value >>> (8 * i));
        }
        return result;
    }
}
