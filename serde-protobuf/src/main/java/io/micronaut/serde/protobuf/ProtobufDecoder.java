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
package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.protobuf.wire.ProtoInput;
import io.micronaut.serde.protobuf.wire.ProtoWire;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Protocol Buffers implementation of {@link Decoder}.
 *
 * <p>The payload carries field numbers, not names, so every structure is decoded against the
 * {@link ProtoSchema} of the type being read into. Fields whose numbers the schema does not know
 * are skipped, which is how protobuf stays forward compatible.</p>
 *
 * <p>Repeated fields arrive in one of two shapes and both are handled: a packed run, which is one
 * length-delimited block of values, or a sequence of separately tagged values.</p>
 *
 * @since 3.2
 */
@Internal
public final class ProtobufDecoder extends LimitingStream implements KeysAwareDecoder {

    private final ProtoInput input;
    private final int limit;
    private final Kind kind;
    private final @Nullable ProtoSchema schema;
    private final @Nullable ProtoProperty owner;

    private @Nullable ProtoProperty currentProperty;
    private int currentWireType = -1;
    private @Nullable String pendingUnknownKey;
    private boolean firstElement;

    private @Nullable Keys currentKeys;
    private int @Nullable [] currentKeyIndexes;

    /**
     * Create a root decoder over a complete protobuf payload.
     *
     * @param payload         The payload
     * @param remainingLimits The nesting limits
     */
    public ProtobufDecoder(byte[] payload, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.input = new ProtoInput(payload);
        this.limit = payload.length;
        this.kind = Kind.ROOT;
        this.schema = null;
        this.owner = null;
    }

    private ProtobufDecoder(ProtoInput input,
                            int limit,
                            Kind kind,
                            @Nullable ProtoSchema schema,
                            @Nullable ProtoProperty owner,
                            RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.input = input;
        this.limit = limit;
        this.kind = kind;
        this.schema = schema;
        this.owner = owner;
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        ProtoSchema childSchema = ProtoSchema.of(type.getType());
        return switch (kind) {
            // the payload itself is the top-level message; it has no tag and no length prefix
            case ROOT -> new ProtobufDecoder(input, limit, Kind.MESSAGE, childSchema, null, childLimits());
            case MESSAGE, REPEATED -> {
                expectWireType(ProtoWire.LENGTH_DELIMITED, "a nested message");
                int length = input.readLength(limit);
                yield new ProtobufDecoder(input, input.position() + length, Kind.MESSAGE, childSchema, currentProperty, childLimits());
            }
            default -> throw new SerdeException("A packed protobuf field cannot contain messages");
        };
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        if (kind == Kind.ROOT) {
            throw new SerdeException("A protobuf payload must be a message. Wrap the collection in a type annotated with @Serdeable.");
        }
        ProtoProperty property = requireCurrent();
        if (currentWireType == ProtoWire.LENGTH_DELIMITED) {
            // a byte[] read element by element is still one bytes field, and a repeated scalar is
            // packed into a single length-delimited run
            if (property.bytesField() || property.packableElement()) {
                int length = input.readLength(limit);
                Kind childKind = property.bytesField() ? Kind.BYTES : Kind.PACKED;
                return new ProtobufDecoder(input, input.position() + length, childKind, schema, property, childLimits());
            }
        }
        // one element has already been positioned by the tag that opened this field; further
        // elements follow as repeats of the same tag, up to the end of the enclosing message
        ProtobufDecoder repeated = new ProtobufDecoder(input, limit, Kind.REPEATED, schema, property, childLimits());
        repeated.currentProperty = property;
        repeated.currentWireType = currentWireType;
        repeated.firstElement = true;
        return repeated;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        switch (kind) {
            case PACKED, BYTES -> {
                return input.position() < limit;
            }
            case REPEATED -> {
                if (firstElement) {
                    // the tag that opened this field already positioned the first element
                    firstElement = false;
                    return true;
                }
                if (input.position() >= limit) {
                    return false;
                }
                int mark = input.position();
                int tag = input.readVarint32();
                if (ProtoWire.fieldNumber(tag) == requireOwner().number()) {
                    currentWireType = ProtoWire.wireType(tag);
                    return true;
                }
                input.position(mark);
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public int decodeKey(Keys keys) throws IOException {
        if (pendingUnknownKey != null) {
            return MATCH_UNKNOWN_NAME;
        }
        ProtoSchema currentSchema = requireSchema();
        int[] keyIndexes = currentKeyIndexes;
        if (keys != currentKeys || keyIndexes == null) {
            keyIndexes = currentSchema.keyIndexBySlot(keys);
            currentKeys = keys;
            currentKeyIndexes = keyIndexes;
        }
        int slot = nextFieldSlot(currentSchema);
        if (slot < 0) {
            return MATCH_END_OBJECT;
        }
        int keyIndex = keyIndexes[slot];
        if (keyIndex == Keys.UNKNOWN_KEY) {
            pendingUnknownKey = currentSchema.propertyAt(slot).name();
            return MATCH_UNKNOWN_NAME;
        }
        return keyIndex;
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        String pending = pendingUnknownKey;
        if (pending != null) {
            pendingUnknownKey = null;
            return pending;
        }
        ProtoSchema currentSchema = requireSchema();
        int slot = nextFieldSlot(currentSchema);
        return slot < 0 ? null : currentSchema.propertyAt(slot).name();
    }

    @Override
    public String decodeString() throws IOException {
        expectWireType(ProtoWire.LENGTH_DELIMITED, "a string");
        return input.readString(limit);
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return readIntegral() != 0;
    }

    @Override
    public byte decodeByte() throws IOException {
        if (kind == Kind.BYTES) {
            return input.readRawByte();
        }
        return (byte) readIntegral();
    }

    @Override
    public short decodeShort() throws IOException {
        return (short) readIntegral();
    }

    @Override
    public char decodeChar() throws IOException {
        return (char) readIntegral();
    }

    @Override
    public int decodeInt() throws IOException {
        return (int) readIntegral();
    }

    @Override
    public long decodeLong() throws IOException {
        return readIntegral();
    }

    @Override
    public float decodeFloat() throws IOException {
        if (valueWireType(ProtoWire.FIXED32) == ProtoWire.FIXED32) {
            return Float.intBitsToFloat(input.readFixed32());
        }
        return readIntegral();
    }

    @Override
    public double decodeDouble() throws IOException {
        int wireType = valueWireType(ProtoWire.FIXED64);
        if (wireType == ProtoWire.FIXED64) {
            return Double.longBitsToDouble(input.readFixed64());
        }
        if (wireType == ProtoWire.FIXED32) {
            return Float.intBitsToFloat(input.readFixed32());
        }
        return readIntegral();
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        if (valueWireType(ProtoWire.LENGTH_DELIMITED) == ProtoWire.LENGTH_DELIMITED) {
            return new BigInteger(input.readString(limit));
        }
        return BigInteger.valueOf(readIntegral());
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        if (valueWireType(ProtoWire.LENGTH_DELIMITED) == ProtoWire.LENGTH_DELIMITED) {
            return new BigDecimal(input.readString(limit));
        }
        return BigDecimal.valueOf(readIntegral());
    }

    @Override
    public byte[] decodeBinary() throws IOException {
        expectWireType(ProtoWire.LENGTH_DELIMITED, "binary data");
        return input.readBytes(limit);
    }

    @Override
    public boolean decodeNull() {
        // an absent protobuf field never reaches the decoder, so a value in hand is never null
        return false;
    }

    @Override
    public Object decodeArbitrary() throws IOException {
        throw unsupportedSchemaFree("an untyped value");
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        throw unsupportedSchemaFree("a tree node");
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        ProtobufDecoder buffered = new ProtobufDecoder(
            new ProtoInput(input.buffer()), limit, kind, schema, owner, ourLimits());
        buffered.input.position(input.position());
        buffered.currentProperty = currentProperty;
        buffered.currentWireType = currentWireType;
        buffered.firstElement = firstElement;
        skipValue();
        return buffered;
    }

    @Override
    public void skipValue() throws IOException {
        input.skip(valueWireType(ProtoWire.VARINT), limit);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        switch (kind) {
            // trailing fields belong to the enclosing payload, so the cursor has to land on the limit
            case MESSAGE, PACKED, BYTES -> input.position(limit);
            default -> {
            }
        }
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return new SerdeException(message);
    }

    private ProtoSchema requireSchema() throws IOException {
        ProtoSchema currentSchema = schema;
        if (kind != Kind.MESSAGE || currentSchema == null) {
            throw new SerdeException("A protobuf property key can only be decoded inside a message");
        }
        return currentSchema;
    }

    private int nextFieldSlot(ProtoSchema currentSchema) throws IOException {
        while (input.position() < limit) {
            int tag = input.readVarint32();
            int wireType = ProtoWire.wireType(tag);
            int slot = currentSchema.slotOf(ProtoWire.fieldNumber(tag));
            if (slot < 0) {
                // a field this version does not know about: skipping keeps readers forward compatible
                input.skip(wireType, limit);
                continue;
            }
            currentProperty = currentSchema.propertyAt(slot);
            currentWireType = wireType;
            return slot;
        }
        return -1;
    }

    private long readIntegral() throws IOException {
        ProtoProperty property = kind == Kind.PACKED ? owner : currentProperty;
        return switch (valueWireType(ProtoWire.VARINT)) {
            case ProtoWire.VARINT -> {
                long raw = input.readVarint64();
                yield property != null && property.intKind() == ProtoProperty.KIND_ZIGZAG
                    ? ProtoWire.zigZagDecode64(raw)
                    : raw;
            }
            case ProtoWire.FIXED32 -> input.readFixed32();
            case ProtoWire.FIXED64 -> input.readFixed64();
            default -> throw new SerdeException("Expected a numeric protobuf value but the field "
                + describeCurrent() + " is length-delimited");
        };
    }

    private int valueWireType(int fallback) {
        if (kind == Kind.PACKED) {
            ProtoProperty property = owner;
            if (property == null) {
                return fallback;
            }
            // a packed run carries no per-element tag, so the representation comes from the schema
            return switch (property.intKind()) {
                case ProtoProperty.KIND_FIXED32 -> ProtoWire.FIXED32;
                case ProtoProperty.KIND_FIXED64 -> ProtoWire.FIXED64;
                case ProtoProperty.KIND_INT32 -> fallback;
                default -> ProtoWire.VARINT;
            };
        }
        return currentWireType < 0 ? fallback : currentWireType;
    }

    private void expectWireType(int expected, String what) throws IOException {
        int actual = valueWireType(expected);
        if (actual != expected) {
            throw new SerdeException("Expected " + what + " but the protobuf field "
                + describeCurrent() + " has wire type " + actual);
        }
    }

    private String describeCurrent() {
        ProtoProperty property = currentProperty != null ? currentProperty : owner;
        return property == null ? "<unknown>" : "[" + property.name() + "] (number " + property.number() + ")";
    }

    private ProtoProperty requireCurrent() throws IOException {
        ProtoProperty property = currentProperty;
        if (property == null) {
            throw new SerdeException("A protobuf value was decoded without a preceding property key");
        }
        return property;
    }

    private ProtoProperty requireOwner() {
        ProtoProperty property = owner;
        if (property == null) {
            throw new IllegalStateException("Repeated protobuf field has no owning property");
        }
        return property;
    }

    private SerdeException unsupportedSchemaFree(String what) {
        return new SerdeException("Protocol Buffers cannot be read without a schema, so " + what
            + " cannot be decoded. Declare a concrete @Serdeable type with @ProtoField numbers instead.");
    }

    private enum Kind {
        ROOT,
        MESSAGE,
        REPEATED,
        PACKED,
        BYTES
    }
}
