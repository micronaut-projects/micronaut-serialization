/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * A serde stream decoder that reads Oracle's binary JSON (OSON) in place from a byte array: no DOM, no event
 * objects, no copy of the document. It is the fast path for Oracle JSON columns, where the driver already holds
 * the encoded document ({@code OracleJsonDatum.shareBytes()}).
 * <p>
 * The layout is a header with flags, a field name dictionary, and a tree segment of nodes whose containers
 * hold child field ids and offsets. Knowledge of that layout comes from the published open source
 * python-oracledb driver (Apache / UPL licensed, {@code impl/base/oson.pyx}), not from the Oracle JDBC driver,
 * whose license forbids decompilation. Field names are interned through an {@link OracleOsonFieldNameCache} so
 * a stream of documents with the same shape allocates no name strings after the first. Requires the AL32UTF8
 * database character set, which is what Oracle 23ai and later use for JSON.
 * <p>
 * Dates and timestamps decode to {@code java.time} values or ISO-8601 strings, intervals to ISO-8601 duration
 * and period strings, binary values (including vectors and ids) to hexadecimal strings or byte arrays, the same
 * shapes the {@link OracleJdbcJsonParserDecoder} produces.
 *
 * @since 3.3.0
 */
@Internal
public final class OracleOsonDecoder extends AbstractStreamDecoder {

    private static final String METHOD_CALLED_IN_WRONG_CONTEXT = "Method called in wrong context ";

    private static final int HEADER_MAGIC_0 = 0xFF;
    private static final int HEADER_MAGIC_1 = 0x4A;
    private static final int HEADER_MAGIC_2 = 0x5A;
    private static final int VERSION_MAX_FNAME_255 = 1;
    private static final int VERSION_MAX_FNAME_65535 = 3;

    private static final int FLAG_REL_OFFSET_MODE = 0x01;
    private static final int FLAG_NUM_FNAMES_UINT32 = 0x08;
    private static final int FLAG_IS_SCALAR = 0x10;
    private static final int FLAG_SEC_FNAMES_SEG_UINT16 = 0x0100;
    private static final int FLAG_NUM_FNAMES_UINT16 = 0x0400;
    private static final int FLAG_FNAMES_SEG_UINT32 = 0x0800;
    private static final int FLAG_TREE_SEG_UINT32 = 0x1000;

    private static final int TYPE_NULL = 0x30;
    private static final int TYPE_TRUE = 0x31;
    private static final int TYPE_FALSE = 0x32;
    private static final int TYPE_STRING_U8 = 0x33;
    private static final int TYPE_NUMBER_U8 = 0x34;
    private static final int TYPE_BINARY_DOUBLE = 0x36;
    private static final int TYPE_STRING_U16 = 0x37;
    private static final int TYPE_STRING_U32 = 0x38;
    private static final int TYPE_TIMESTAMP = 0x39;
    private static final int TYPE_BINARY_U16 = 0x3a;
    private static final int TYPE_BINARY_U32 = 0x3b;
    private static final int TYPE_DATE = 0x3c;
    private static final int TYPE_INTERVAL_YM = 0x3d;
    private static final int TYPE_INTERVAL_DS = 0x3e;
    private static final int TYPE_EXTENDED = 0x7b;
    private static final int TYPE_TIMESTAMP_TZ = 0x7c;
    private static final int TYPE_TIMESTAMP7 = 0x7d;
    private static final int TYPE_ID = 0x7e;
    private static final int TYPE_BINARY_FLOAT = 0x7f;
    private static final int EXTENDED_VECTOR = 0x01;

    private static final int CONTAINER_BIT = 0x80;
    private static final int ARRAY_BIT = 0x40;
    private static final int OFFSETS_UINT32_BIT = 0x20;
    private static final int CHILDREN_BITS = 0x18;
    private static final int CHILDREN_UINT16 = 0x08;
    private static final int CHILDREN_SHARED = 0x18;

    private static final int DATE_LENGTH = 7;
    private static final int TIMESTAMP_LENGTH = 11;
    private static final int TIMESTAMP_TZ_LENGTH = 13;
    private static final int INTERVAL_YM_LENGTH = 5;
    private static final int INTERVAL_DS_LENGTH = 11;
    private static final int TZ_HOUR_OFFSET = 20;
    private static final int TZ_MINUTE_OFFSET = 60;
    private static final int TZ_REGION_ID_BIT = 0x80;
    private static final long DURATION_MID = 0x80000000L;
    private static final int DURATION_OFFSET = 60;

    /** Scalar kinds recorded for the current value. */
    private static final int SCALAR_STRING = 1;
    private static final int SCALAR_NUMBER = 2;
    private static final int SCALAR_DOUBLE = 3;
    private static final int SCALAR_FLOAT = 4;
    private static final int SCALAR_BOOLEAN = 5;
    private static final int SCALAR_NULL = 6;
    private static final int SCALAR_BINARY = 7;
    private static final int SCALAR_DATE = 8;
    private static final int SCALAR_TIMESTAMP = 9;
    private static final int SCALAR_TIMESTAMP_TZ = 10;
    private static final int SCALAR_INTERVAL_YM = 11;
    private static final int SCALAR_INTERVAL_DS = 12;

    private static final int INITIAL_FRAMES = 8;
    private static final String[] NO_NAMES = new String[0];

    private final OracleOsonFieldNameCache fieldNameCache;
    private final byte[] data;
    private int treeSegPos;
    private boolean relativeOffsets;
    private int fieldIdLength;
    private String[] fieldNames = NO_NAMES;

    // container frames as parallel arrays, index 0 is the outermost
    private boolean[] frameObject = new boolean[INITIAL_FRAMES];
    private int[] frameCount = new int[INITIAL_FRAMES];
    private int[] frameIndex = new int[INITIAL_FRAMES];
    private int[] frameFieldIdsPos = new int[INITIAL_FRAMES];
    private int[] frameOffsetsPos = new int[INITIAL_FRAMES];
    private int[] frameOffsetSize = new int[INITIAL_FRAMES];
    private int[] frameContainerOffset = new int[INITIAL_FRAMES];
    private int depth = -1;

    @Nullable
    private TokenType token;
    private int scalarKind;
    private int scalarPos;
    private int scalarLength;
    private boolean scalarBoolean;

    /**
     * Creates a decoder positioned on the first token of a document.
     *
     * @param data            the OSON document
     * @param offset          the offset of the document in {@code data}
     * @param length          the length of the document
     * @param remainingLimits the nesting limits
     * @param coercionPolicy  the coercion policy
     * @param fieldNameCache  the field name cache shared between documents
     * @throws IOException if the bytes are not an OSON document this decoder understands
     */
    public OracleOsonDecoder(byte[] data,
                             int offset,
                             int length,
                             RemainingLimits remainingLimits,
                             CoercionPolicy coercionPolicy,
                             OracleOsonFieldNameCache fieldNameCache) throws IOException {
        super(remainingLimits, coercionPolicy);
        this.data = data;
        this.fieldNameCache = fieldNameCache;
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new SerdeException("OSON document range [" + offset + ", " + (offset + length) + ") is outside the buffer");
        }
        try {
            readHeader(offset, length);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new SerdeException("Truncated OSON document", e);
        }
    }

    // ---- header -------------------------------------------------------------------------------------------

    private void readHeader(int offset, int length) throws IOException {
        if (length < 6 || u8(offset) != HEADER_MAGIC_0 || u8(offset + 1) != HEADER_MAGIC_1 || u8(offset + 2) != HEADER_MAGIC_2) {
            throw new SerdeException("Not an OSON document");
        }
        int pos = offset + 3;
        int version = u8(pos++);
        if (version != VERSION_MAX_FNAME_255 && version != VERSION_MAX_FNAME_65535) {
            throw new SerdeException("Unsupported OSON version " + version);
        }
        int primaryFlags = u16(pos);
        pos += 2;
        relativeOffsets = (primaryFlags & FLAG_REL_OFFSET_MODE) != 0;
        if ((primaryFlags & FLAG_IS_SCALAR) != 0) {
            pos += (primaryFlags & FLAG_TREE_SEG_UINT32) != 0 ? 4 : 2;
            treeSegPos = pos;
            token = readNode(pos);
            return;
        }
        int numShort;
        if ((primaryFlags & FLAG_NUM_FNAMES_UINT32) != 0) {
            numShort = i32(pos);
            pos += 4;
            fieldIdLength = 4;
        } else if ((primaryFlags & FLAG_NUM_FNAMES_UINT16) != 0) {
            numShort = u16(pos);
            pos += 2;
            fieldIdLength = 2;
        } else {
            numShort = u8(pos);
            pos += 1;
            fieldIdLength = 1;
        }
        int shortOffsetsSize;
        int shortSegSize;
        if ((primaryFlags & FLAG_FNAMES_SEG_UINT32) != 0) {
            shortOffsetsSize = 4;
            shortSegSize = i32(pos);
            pos += 4;
        } else {
            shortOffsetsSize = 2;
            shortSegSize = u16(pos);
            pos += 2;
        }
        int numLong = 0;
        int longOffsetsSize = 0;
        int longSegSize = 0;
        if (version == VERSION_MAX_FNAME_65535) {
            int secondaryFlags = u16(pos);
            pos += 2;
            longOffsetsSize = (secondaryFlags & FLAG_SEC_FNAMES_SEG_UINT16) != 0 ? 2 : 4;
            numLong = i32(pos);
            pos += 4;
            longSegSize = i32(pos);
            pos += 4;
        }
        pos += (primaryFlags & FLAG_TREE_SEG_UINT32) != 0 ? 4 : 2; // tree segment size
        pos += 2; // number of tiny nodes
        if (numShort + numLong > 0) {
            fieldNames = new String[numShort + numLong];
        }
        if (numShort > 0) {
            pos += numShort; // hash ids
            int offsetsPos = pos;
            pos += numShort * shortOffsetsSize;
            int segPos = pos;
            for (int i = 0; i < numShort; i++) {
                int nameOffset = shortOffsetsSize == 2 ? u16(offsetsPos + i * 2) : i32(offsetsPos + i * 4);
                int nameLength = u8(segPos + nameOffset);
                fieldNames[i] = fieldNameCache.resolve(data, segPos + nameOffset + 1, nameLength);
            }
            pos += shortSegSize;
        }
        if (numLong > 0) {
            pos += numLong * 2; // hash ids
            int offsetsPos = pos;
            pos += numLong * longOffsetsSize;
            int segPos = pos;
            for (int i = 0; i < numLong; i++) {
                int nameOffset = longOffsetsSize == 2 ? u16(offsetsPos + i * 2) : i32(offsetsPos + i * 4);
                int nameLength = u16(segPos + nameOffset);
                fieldNames[numShort + i] = fieldNameCache.resolve(data, segPos + nameOffset + 2, nameLength);
            }
            pos += longSegSize;
        }
        treeSegPos = pos;
        token = readNode(pos);
    }

    // ---- byte access ---------------------------------------------------------------------------------------

    private int u8(int pos) {
        return data[pos] & 0xFF;
    }

    private int u16(int pos) {
        return (data[pos] & 0xFF) << 8 | (data[pos + 1] & 0xFF);
    }

    private int i32(int pos) {
        return (data[pos] & 0xFF) << 24 | (data[pos + 1] & 0xFF) << 16 | (data[pos + 2] & 0xFF) << 8 | (data[pos + 3] & 0xFF);
    }

    private long i64(int pos) {
        return (long) i32(pos) << 32 | (i32(pos + 4) & 0xFFFFFFFFL);
    }

    // ---- tree walking ---------------------------------------------------------------------------------------

    /**
     * Reads the node at a position: pushes a frame for a container, records a scalar otherwise.
     */
    private TokenType readNode(int pos) {
        int type = u8(pos);
        int p = pos + 1;
        if ((type & CONTAINER_BIT) != 0) {
            return pushContainer(type, pos, p);
        }
        switch (type) {
            case TYPE_NULL -> {
                return scalar(SCALAR_NULL, p, 0);
            }
            case TYPE_TRUE -> {
                scalarBoolean = true;
                return scalar(SCALAR_BOOLEAN, p, 0);
            }
            case TYPE_FALSE -> {
                scalarBoolean = false;
                return scalar(SCALAR_BOOLEAN, p, 0);
            }
            case TYPE_STRING_U8 -> {
                return scalar(SCALAR_STRING, p + 1, u8(p));
            }
            case TYPE_STRING_U16 -> {
                return scalar(SCALAR_STRING, p + 2, u16(p));
            }
            case TYPE_STRING_U32 -> {
                return scalar(SCALAR_STRING, p + 4, i32(p));
            }
            case TYPE_NUMBER_U8 -> {
                return scalar(SCALAR_NUMBER, p + 1, u8(p));
            }
            case TYPE_ID -> {
                return scalar(SCALAR_BINARY, p + 1, u8(p));
            }
            case TYPE_BINARY_DOUBLE -> {
                return scalar(SCALAR_DOUBLE, p, 8);
            }
            case TYPE_BINARY_FLOAT -> {
                return scalar(SCALAR_FLOAT, p, 4);
            }
            case TYPE_DATE, TYPE_TIMESTAMP7 -> {
                return scalar(SCALAR_DATE, p, DATE_LENGTH);
            }
            case TYPE_TIMESTAMP -> {
                return scalar(SCALAR_TIMESTAMP, p, TIMESTAMP_LENGTH);
            }
            case TYPE_TIMESTAMP_TZ -> {
                return scalar(SCALAR_TIMESTAMP_TZ, p, TIMESTAMP_TZ_LENGTH);
            }
            case TYPE_INTERVAL_YM -> {
                return scalar(SCALAR_INTERVAL_YM, p, INTERVAL_YM_LENGTH);
            }
            case TYPE_INTERVAL_DS -> {
                return scalar(SCALAR_INTERVAL_DS, p, INTERVAL_DS_LENGTH);
            }
            case TYPE_BINARY_U16 -> {
                return scalar(SCALAR_BINARY, p + 2, u16(p));
            }
            case TYPE_BINARY_U32 -> {
                return scalar(SCALAR_BINARY, p + 4, i32(p));
            }
            case TYPE_EXTENDED -> {
                int subtype = u8(p);
                if (subtype == EXTENDED_VECTOR) {
                    return scalar(SCALAR_BINARY, p + 5, i32(p + 1));
                }
                throw new IllegalStateException("Unsupported OSON extended node " + subtype);
            }
            default -> {
                int high = type & 0xF0;
                if (high == 0x20 || high == 0x60) {
                    // NUMBER with the length minus one in the low nibble
                    return scalar(SCALAR_NUMBER, p, (type & 0x0F) + 1);
                }
                if (high == 0x40 || high == 0x50) {
                    // NUMBER with the length in the low nibble
                    return scalar(SCALAR_NUMBER, p, type & 0x0F);
                }
                if ((type & 0xE0) == 0) {
                    // short string with the length in the type byte
                    return scalar(SCALAR_STRING, p, type);
                }
                throw new IllegalStateException("Unsupported OSON node type 0x" + Integer.toHexString(type));
            }
        }
    }

    private TokenType scalar(int kind, int pos, int length) {
        scalarKind = kind;
        scalarPos = pos;
        scalarLength = length;
        return switch (kind) {
            case SCALAR_NULL -> TokenType.NULL;
            case SCALAR_BOOLEAN -> TokenType.BOOLEAN;
            case SCALAR_NUMBER, SCALAR_DOUBLE, SCALAR_FLOAT -> TokenType.NUMBER;
            default -> TokenType.STRING;
        };
    }

    private TokenType pushContainer(int type, int typePos, int p) {
        boolean object = (type & ARRAY_BIT) == 0;
        int childrenBits = type & CHILDREN_BITS;
        int containerOffset = typePos - treeSegPos;
        int offsetSize = (type & OFFSETS_UINT32_BIT) != 0 ? 4 : 2;
        int count;
        int fieldIdsPos;
        int offsetsPos;
        if (childrenBits == CHILDREN_SHARED) {
            // field ids shared with another object: the offset of that object follows, then this one's offsets
            int sharedOffset = offsetSize == 4 ? i32(p) : u16(p);
            p += offsetSize;
            offsetsPos = p;
            int sharedPos = treeSegPos + sharedOffset;
            int sharedType = u8(sharedPos);
            int sp = sharedPos + 1;
            int sharedBits = sharedType & CHILDREN_BITS;
            if (sharedBits == 0) {
                count = u8(sp);
                sp += 1;
            } else if (sharedBits == CHILDREN_UINT16) {
                count = u16(sp);
                sp += 2;
            } else {
                count = i32(sp);
                sp += 4;
            }
            fieldIdsPos = sp;
        } else {
            if (childrenBits == 0) {
                count = u8(p);
                p += 1;
            } else if (childrenBits == CHILDREN_UINT16) {
                count = u16(p);
                p += 2;
            } else {
                count = i32(p);
                p += 4;
            }
            if (object) {
                fieldIdsPos = p;
                offsetsPos = p + fieldIdLength * count;
            } else {
                fieldIdsPos = 0;
                offsetsPos = p;
            }
        }
        int d = ++depth;
        if (d == frameCount.length) {
            growFrames();
        }
        frameObject[d] = object;
        frameCount[d] = count;
        frameIndex[d] = -1;
        frameFieldIdsPos[d] = fieldIdsPos;
        frameOffsetsPos[d] = offsetsPos;
        frameOffsetSize[d] = offsetSize;
        frameContainerOffset[d] = containerOffset;
        return object ? TokenType.START_OBJECT : TokenType.START_ARRAY;
    }

    private void growFrames() {
        int n = frameCount.length * 2;
        frameObject = Arrays.copyOf(frameObject, n);
        frameCount = Arrays.copyOf(frameCount, n);
        frameIndex = Arrays.copyOf(frameIndex, n);
        frameFieldIdsPos = Arrays.copyOf(frameFieldIdsPos, n);
        frameOffsetsPos = Arrays.copyOf(frameOffsetsPos, n);
        frameOffsetSize = Arrays.copyOf(frameOffsetSize, n);
        frameContainerOffset = Arrays.copyOf(frameContainerOffset, n);
    }

    private int childPos(int d, int index) {
        int offset = frameOffsetSize[d] == 4 ? i32(frameOffsetsPos[d] + index * 4) : u16(frameOffsetsPos[d] + index * 2);
        if (relativeOffsets) {
            offset += frameContainerOffset[d];
        }
        return treeSegPos + offset;
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        // move past the end token to the parent's next key or end
        nextToken();
    }

    @Override
    protected @Nullable TokenType currentToken() {
        return token;
    }

    @Override
    protected void nextToken() {
        int d = depth;
        if (d < 0) {
            // past the end of the document
            token = null;
            return;
        }
        TokenType current = token;
        if (current == TokenType.END_OBJECT || current == TokenType.END_ARRAY) {
            // leaving a container: continue with the parent's next element
            depth = --d;
            if (d < 0) {
                token = null;
                return;
            }
        } else if (current == TokenType.KEY) {
            // after a key comes its value
            token = readNode(childPos(d, frameIndex[d]));
            return;
        }
        int index = ++frameIndex[d];
        if (index >= frameCount[d]) {
            token = frameObject[d] ? TokenType.END_OBJECT : TokenType.END_ARRAY;
        } else if (frameObject[d]) {
            token = TokenType.KEY;
        } else {
            token = readNode(childPos(d, index));
        }
    }

    @Override
    protected String getCurrentKey() {
        int d = depth;
        int pos = frameFieldIdsPos[d] + frameIndex[d] * fieldIdLength;
        int fieldId = switch (fieldIdLength) {
            case 1 -> u8(pos);
            case 2 -> u16(pos);
            default -> i32(pos);
        };
        return fieldNames[fieldId - 1];
    }

    @Override
    protected void skipChildren() {
        if (token == TokenType.START_OBJECT) {
            token = TokenType.END_OBJECT;
        } else if (token == TokenType.START_ARRAY) {
            token = TokenType.END_ARRAY;
        }
    }

    // ---- scalars -------------------------------------------------------------------------------------------

    @Override
    protected String coerceScalarToString(TokenType currentToken) {
        return switch (scalarKind) {
            case SCALAR_BOOLEAN -> scalarBoolean ? StringUtils.TRUE : StringUtils.FALSE;
            case SCALAR_NULL -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentToken);
            default -> getString();
        };
    }

    @Override
    protected String getString() {
        return switch (scalarKind) {
            case SCALAR_STRING -> new String(data, scalarPos, scalarLength, StandardCharsets.UTF_8);
            case SCALAR_NUMBER -> OracleOsonNumber.toPlainString(data, scalarPos, scalarLength);
            case SCALAR_DOUBLE -> Double.toString(getDouble());
            case SCALAR_FLOAT -> Float.toString(getFloat());
            case SCALAR_BOOLEAN -> Boolean.toString(scalarBoolean);
            case SCALAR_DATE, SCALAR_TIMESTAMP -> localDateTime().toString();
            case SCALAR_TIMESTAMP_TZ -> offsetDateTime().toString();
            case SCALAR_INTERVAL_YM -> period().toString();
            case SCALAR_INTERVAL_DS -> duration().toString();
            case SCALAR_BINARY -> HexFormat.of().withUpperCase().formatHex(data, scalarPos, scalarPos + scalarLength);
            default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
        };
    }

    private LocalDateTime localDateTime() {
        int o = scalarPos;
        int year = (u8(o) - 100) * 100 + u8(o + 1) - 100;
        int nanos = scalarLength >= TIMESTAMP_LENGTH ? i32(o + 7) : 0;
        return LocalDateTime.of(year, u8(o + 2), u8(o + 3), u8(o + 4) - 1, u8(o + 5) - 1, u8(o + 6) - 1, nanos);
    }

    /**
     * A timestamp with time zone stores the UTC instant followed by the offset.
     */
    private OffsetDateTime offsetDateTime() {
        int tzByte = u8(scalarPos + 11);
        if ((tzByte & TZ_REGION_ID_BIT) != 0) {
            throw new IllegalStateException("OSON timestamps with a time zone region are not supported");
        }
        int seconds = (tzByte - TZ_HOUR_OFFSET) * 3600 + (u8(scalarPos + 12) - TZ_MINUTE_OFFSET) * 60;
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(seconds);
        return OffsetDateTime.of(localDateTime().plusSeconds(seconds), offset);
    }

    private Period period() {
        int o = scalarPos;
        int years = (int) ((i32(o) & 0xFFFFFFFFL) - DURATION_MID);
        int months = u8(o + 4) - DURATION_OFFSET;
        return Period.of(years, months, 0);
    }

    private Duration duration() {
        int o = scalarPos;
        long days = (i32(o) & 0xFFFFFFFFL) - DURATION_MID;
        int hours = u8(o + 4) - DURATION_OFFSET;
        int minutes = u8(o + 5) - DURATION_OFFSET;
        int seconds = u8(o + 6) - DURATION_OFFSET;
        long nanos = (i32(o + 7) & 0xFFFFFFFFL) - DURATION_MID;
        return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds).plusNanos(nanos);
    }

    private byte[] bytes() {
        return Arrays.copyOfRange(data, scalarPos, scalarPos + scalarLength);
    }

    private float getFloat() {
        int bits = i32(scalarPos);
        bits = (bits & Integer.MIN_VALUE) != 0 ? bits & Integer.MAX_VALUE : ~bits;
        return Float.intBitsToFloat(bits);
    }

    @Override
    protected boolean getBoolean() {
        return scalarBoolean;
    }

    @Override
    protected long getLong() {
        return switch (scalarKind) {
            case SCALAR_NUMBER -> OracleOsonNumber.toLong(data, scalarPos, scalarLength);
            case SCALAR_DOUBLE -> (long) getDouble();
            case SCALAR_FLOAT -> (long) getFloat();
            default -> Long.parseLong(getString());
        };
    }

    @Override
    protected double getDouble() {
        return switch (scalarKind) {
            case SCALAR_NUMBER -> OracleOsonNumber.toDouble(data, scalarPos, scalarLength);
            case SCALAR_DOUBLE -> {
                // binary double is stored sortable: positive values have the sign bit set, negative ones are complemented
                long bits = i64(scalarPos);
                bits = (bits & Long.MIN_VALUE) != 0 ? bits & Long.MAX_VALUE : ~bits;
                yield Double.longBitsToDouble(bits);
            }
            case SCALAR_FLOAT -> getFloat();
            default -> Double.parseDouble(getString());
        };
    }

    @Override
    protected BigInteger getBigInteger() {
        return getBigDecimal().toBigInteger();
    }

    @Override
    protected BigDecimal getBigDecimal() {
        return switch (scalarKind) {
            case SCALAR_NUMBER -> OracleOsonNumber.toBigDecimal(data, scalarPos, scalarLength);
            case SCALAR_DOUBLE, SCALAR_FLOAT -> BigDecimal.valueOf(getDouble());
            default -> new BigDecimal(getString());
        };
    }

    @Override
    protected Number getBestNumber() {
        return switch (scalarKind) {
            case SCALAR_NUMBER -> OracleOsonNumber.toBigDecimal(data, scalarPos, scalarLength);
            case SCALAR_DOUBLE -> getDouble();
            case SCALAR_FLOAT -> getFloat();
            default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
        };
    }

    @Override
    protected boolean isCurrentNumberFloat() {
        return scalarKind != SCALAR_NUMBER || !OracleOsonNumber.isIntegral(data, scalarPos, scalarLength);
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message, null, invalidValue);
        } else {
            return new SerdeException(message);
        }
    }

    /**
     * Decodes an OSON binary value, or a string holding hexadecimal, or an array of bytes, as a byte array.
     *
     * @return the bytes
     */
    @Override
    public byte[] decodeBinary() throws IOException {
        if (token == TokenType.STRING) {
            byte[] bytes = switch (scalarKind) {
                case SCALAR_BINARY -> bytes();
                case SCALAR_STRING -> HexFormat.of().parseHex(getString());
                default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
            };
            nextToken();
            return bytes;
        }
        if (token == TokenType.START_ARRAY) {
            return super.decodeBinary();
        }
        throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
    }

    private void requireScalar() {
        if (token != TokenType.STRING) {
            throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
        }
    }

    /**
     * Decodes an OSON date or timestamp, or an ISO-8601 string, as a {@link LocalDateTime}.
     *
     * @return the value
     */
    public LocalDateTime decodeLocalDateTime() {
        requireScalar();
        LocalDateTime value = switch (scalarKind) {
            case SCALAR_DATE, SCALAR_TIMESTAMP -> localDateTime();
            case SCALAR_TIMESTAMP_TZ -> offsetDateTime().toLocalDateTime();
            case SCALAR_STRING -> LocalDateTime.parse(getString());
            default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
        };
        nextToken();
        return value;
    }

    /**
     * Decodes an OSON timestamp with time zone, or an ISO-8601 string, as an {@link OffsetDateTime}.
     *
     * @return the value
     */
    public OffsetDateTime decodeOffsetDateTime() {
        requireScalar();
        OffsetDateTime value = switch (scalarKind) {
            case SCALAR_TIMESTAMP_TZ -> offsetDateTime();
            case SCALAR_STRING -> OffsetDateTime.parse(getString());
            default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
        };
        nextToken();
        return value;
    }

    /**
     * Decodes an OSON timestamp with time zone, or an ISO-8601 string, as a {@link ZonedDateTime}.
     *
     * @return the value
     */
    public ZonedDateTime decodeZonedDateTime() {
        requireScalar();
        ZonedDateTime value = switch (scalarKind) {
            case SCALAR_TIMESTAMP_TZ -> offsetDateTime().toZonedDateTime();
            case SCALAR_STRING -> ZonedDateTime.parse(getString());
            default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + token);
        };
        nextToken();
        return value;
    }
}
