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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Deserializer.DecoderContext;
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

    /**
     * The longest digit string accepted for {@code BigInteger} and {@code BigDecimal}, matching
     * Jackson's {@code StreamReadConstraints} default.
     */
    private static final int MAX_NUMBER_LENGTH = 1000;

    /**
     * How many times the payload's own length may be spent on normalized copies of its messages.
     *
     * <p>Each nested message is normalized into a fresh array, and every copy along the current
     * path stays reachable until its parent finishes, so a deeply nested payload can retain many
     * times its own size. Legitimate documents partition their bytes between siblings and stay far
     * below this; a payload built so each child is nearly as large as its parent does not.</p>
     */
    private static final long NORMALIZED_BUDGET_FACTOR = 16L;

    private static final long MIN_NORMALIZED_BUDGET = 1L << 20;

    private final ProtoInput input;
    private final int limit;
    private final Kind kind;
    private final @Nullable ProtoSchema schema;
    private final @Nullable ProtoProperty owner;
    private final PayloadScope scope;

    private @Nullable ProtoProperty currentProperty;
    private int currentWireType = -1;
    private @Nullable String pendingUnknownKey;
    private boolean firstElement;
    private boolean valuePending;

    private @Nullable Keys currentKeys;
    private int @Nullable [] currentKeyIndexes;

    /**
     * Create a root decoder over a complete protobuf payload.
     *
     * @param payload         The payload
     * @param remainingLimits The nesting limits
     * @param decoderContext  The decoder context, used to resolve deserializers for nested values
     */
    public ProtobufDecoder(byte[] payload,
                           RemainingLimits remainingLimits,
                           DecoderContext decoderContext) {
        super(remainingLimits);
        this.input = new ProtoInput(payload);
        this.limit = payload.length;
        this.kind = Kind.ROOT;
        this.schema = null;
        this.owner = null;
        // the budget is a long: for a payload past ~128 MiB an int multiply overflows, and the max
        // would then pick the 1 MiB floor and reject a perfectly valid message
        PayloadScope payloadScope = new PayloadScope(decoderContext, new NormalizationBudget(
            Math.max(MIN_NORMALIZED_BUDGET, payload.length * NORMALIZED_BUDGET_FACTOR)));
        this.scope = payloadScope;
    }

    private ProtobufDecoder(ProtoInput input,
                            int limit,
                            Kind kind,
                            @Nullable ProtoSchema schema,
                            @Nullable ProtoProperty owner,
                            PayloadScope scope,
                            RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.input = input;
        this.limit = limit;
        this.kind = kind;
        this.schema = schema;
        this.owner = owner;
        this.scope = scope;
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        ProtoSchema childSchema = ProtoSchema.of(type);
        return switch (kind) {
            // the payload itself is the top-level message; it has no tag and no length prefix
            case ROOT -> {
                byte[] normalized = ProtoMessageNormalizer.normalize(input.buffer(), input.position(), limit, childSchema);
                scope.budget().charge(normalized.length);
                input.position(limit);
                yield new ProtobufDecoder(new ProtoInput(normalized), normalized.length, Kind.MESSAGE, childSchema, null,
                    scope, childLimits());
            }
            case MESSAGE, REPEATED -> {
                expectWireType(ProtoWire.LENGTH_DELIMITED, "a nested message");
                int length = input.readLength(limit);
                int start = input.position();
                int end = start + length;
                input.position(end);
                valueConsumed();
                byte[] normalized = ProtoMessageNormalizer.normalize(input.buffer(), start, end, childSchema);
                scope.budget().charge(normalized.length);
                yield new ProtobufDecoder(new ProtoInput(normalized), normalized.length, Kind.MESSAGE, childSchema, currentProperty,
                    scope, childLimits());
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
        if (kind == Kind.REPEATED && property.repeatedBytesField()) {
            expectWireType(ProtoWire.LENGTH_DELIMITED, "binary data");
            int length = input.readLength(limit);
            valueConsumed();
            return new ProtobufDecoder(input, input.position() + length, Kind.BYTES, schema, property,
                scope, childLimits());
        }
        // a byte[] read element by element is still one bytes field, and a repeated scalar is
        // packed into a single length-delimited run
        if (currentWireType == ProtoWire.LENGTH_DELIMITED
            && (property.bytesField() || property.packableElement())) {
            int length = input.readLength(limit);
            Kind childKind = property.bytesField() ? Kind.BYTES : Kind.PACKED;
            return new ProtobufDecoder(input, input.position() + length, childKind, schema, property,
                scope, childLimits());
        }
        // one element has already been positioned by the tag that opened this field; further
        // elements follow as repeats of the same tag, up to the end of the enclosing message
        ProtobufDecoder repeated = new ProtobufDecoder(input, limit, Kind.REPEATED, schema, property,
            scope, childLimits());
        repeated.currentProperty = property;
        repeated.currentWireType = currentWireType;
        repeated.firstElement = true;
        return repeated;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        if (valuePending) {
            return true;
        }
        switch (kind) {
            case PACKED, BYTES -> {
                valuePending = input.position() < limit;
                return valuePending;
            }
            case REPEATED -> {
                if (firstElement) {
                    // the tag that opened this field already positioned the first element
                    firstElement = false;
                    valuePending = true;
                    return true;
                }
                if (input.position() >= limit) {
                    return false;
                }
                int mark = input.position();
                int tag = input.readTag();
                if (ProtoWire.fieldNumber(tag) == requireOwner().number()) {
                    currentWireType = ProtoWire.wireType(tag);
                    valuePending = true;
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
        String value = input.readString(limit);
        valueConsumed();
        return value;
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return readIntegral() != 0;
    }

    @Override
    public byte decodeByte() throws IOException {
        if (kind == Kind.BYTES) {
            byte value = input.readRawByte();
            valueConsumed();
            return value;
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
        expectWireType(ProtoWire.FIXED32, "a float");
        float value = Float.intBitsToFloat(input.readFixed32(limit));
        valueConsumed();
        return value;
    }

    @Override
    public double decodeDouble() throws IOException {
        expectWireType(ProtoWire.FIXED64, "a double");
        double value = Double.longBitsToDouble(input.readFixed64(limit));
        valueConsumed();
        return value;
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        return new BigInteger(decodeNumberText("an integer"));
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        return new BigDecimal(decodeNumberText("a decimal"));
    }

    /**
     * Read the text of an arbitrary-precision number, refusing one long enough to be a denial of
     * service.
     *
     * <p>Parsing a decimal string into a {@link BigInteger} is superlinear, so an unbounded digit
     * string lets a payload of a few megabytes hold a request thread for minutes. Jackson caps this
     * the same way through {@code StreamReadConstraints}, and the limit here matches its default.
     * A number longer than this has no protobuf representation anyway &mdash; it travels as a
     * string only because the format has no arbitrary-precision type.</p>
     */
    private String decodeNumberText(String what) throws IOException {
        String text = decodeString();
        if (text.length() > MAX_NUMBER_LENGTH) {
            throw new SerdeException("The protobuf field " + describeCurrent() + " carries " + what
                + " of " + text.length() + " characters, above the limit of " + MAX_NUMBER_LENGTH + ".");
        }
        return text;
    }

    @Override
    public byte[] decodeBinary() throws IOException {
        expectWireType(ProtoWire.LENGTH_DELIMITED, "binary data");
        byte[] value = input.readBytes(limit);
        valueConsumed();
        return value;
    }

    @Override
    public boolean decodeNull() {
        // an absent protobuf field never reaches the decoder, so a value in hand is never null
        return false;
    }

    @Override
    public Object decodeArbitrary() throws IOException {
        ProtoProperty property = requireCurrent();
        Argument<?> argument = property.argument();
        if (argument.equalsType(Argument.OBJECT_ARGUMENT)) {
            throw unsupportedSchemaFree("an untyped value");
        }
        return decodeResolvedValue(scope.decoderContext(), argument);
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        throw unsupportedSchemaFree("a tree node");
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        ProtobufDecoder buffered = new ProtobufDecoder(
            new ProtoInput(input.buffer()), limit, kind, schema, owner, scope, ourLimits());
        buffered.input.position(input.position());
        buffered.currentProperty = currentProperty;
        buffered.currentWireType = currentWireType;
        buffered.firstElement = firstElement;
        buffered.valuePending = valuePending;
        skipValue();
        return buffered;
    }

    @Override
    public void skipValue() throws IOException {
        ProtoProperty property = currentProperty;
        int wireType = valueWireType(ProtoWire.VARINT);
        input.skip(wireType, limit);
        if (kind == Kind.REPEATED) {
            firstElement = false;
        }
        valueConsumed();
        if (kind == Kind.MESSAGE && property != null && property.repeatedField()) {
            skipFollowingOccurrences(property);
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        switch (kind) {
            case MESSAGE, PACKED, BYTES -> finishBoundedStructure(consumeLeftElements);
            case REPEATED -> finishRepeatedField(consumeLeftElements);
            case ROOT -> {
                // the root is not a structure of its own, so there is nothing to close
            }
            default -> throw new IllegalStateException("Unsupported decoder kind: " + kind);
        }
    }

    private void finishBoundedStructure(boolean consumeLeftElements) {
        if (input.position() >= limit) {
            return;
        }
        if (!consumeLeftElements) {
            throw new IllegalStateException("There are unread protobuf values in the current structure");
        }
        input.position(limit);
        valuePending = false;
    }

    private void finishRepeatedField(boolean consumeLeftElements) throws IOException {
        if (!hasRemainingRepeatedValue()) {
            return;
        }
        if (!consumeLeftElements) {
            throw new IllegalStateException("There are unread protobuf values in the current repeated field");
        }
        consumeRemainingRepeatedValues();
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
            int tag = input.readTag();
            int wireType = ProtoWire.wireType(tag);
            int fieldNumber = ProtoWire.fieldNumber(tag);
            int slot = currentSchema.slotOf(fieldNumber);
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
        int expectedWireType = expectedWireType(property);
        int actualWireType = valueWireType(expectedWireType);
        if (actualWireType != expectedWireType) {
            throw new SerdeException("Expected a numeric protobuf value with wire type " + expectedWireType
                + " but the field " + describeCurrent() + " has wire type " + actualWireType);
        }
        long value = switch (expectedWireType) {
            case ProtoWire.VARINT -> {
                long raw = input.readVarint64(limit);
                if (property == null || property.intKind() != ProtoProperty.KIND_ZIGZAG) {
                    yield raw;
                }
                // sint32 is zig-zagged over 32 bits, so an oversized varint has to be narrowed
                // first: decoding all 64 bits and truncating afterwards gives a different value
                yield property.zigZag32()
                    ? ProtoWire.zigZagDecode32((int) raw)
                    : ProtoWire.zigZagDecode64(raw);
            }
            case ProtoWire.FIXED32 -> input.readFixed32(limit);
            case ProtoWire.FIXED64 -> input.readFixed64(limit);
            default -> throw new SerdeException("Expected a numeric protobuf value but the field "
                + describeCurrent() + " is length-delimited");
        };
        valueConsumed();
        return value;
    }

    /**
     * The wire type a numeric value is expected to arrive under.
     *
     * <p>For the types protobuf models this comes from the schema, so a payload that disagrees is
     * reported rather than misread. An opaque property is serialized by a serde of its own choosing
     * and the schema has only a guess, so there the tag on the payload decides. A packed run has no
     * per-element tag, leaving the schema as the only source either way.</p>
     */
    private int expectedWireType(@Nullable ProtoProperty property) {
        if (property == null) {
            return ProtoWire.VARINT;
        }
        if (property.opaqueWireType() && kind != Kind.PACKED && currentWireType >= 0) {
            return currentWireType;
        }
        return property.wireType();
    }

    private int valueWireType(int fallback) {
        if (kind == Kind.PACKED) {
            ProtoProperty property = owner;
            // a packed run carries no per-element tag, so the representation comes from the schema
            return property == null ? fallback : property.wireType();
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

    private void valueConsumed() {
        valuePending = false;
    }

    private void skipFollowingOccurrences(ProtoProperty property) throws IOException {
        while (input.position() < limit) {
            int mark = input.position();
            int tag = input.readTag();
            int fieldNumber = ProtoWire.fieldNumber(tag);
            if (fieldNumber != property.number()) {
                input.position(mark);
                return;
            }
            input.skip(ProtoWire.wireType(tag), limit);
        }
    }

    private boolean hasRemainingRepeatedValue() throws IOException {
        if (firstElement || valuePending) {
            return true;
        }
        if (input.position() >= limit) {
            return false;
        }
        int mark = input.position();
        int tag = input.readVarint32();
        input.position(mark);
        return ProtoWire.fieldNumber(tag) == requireOwner().number();
    }

    private void consumeRemainingRepeatedValues() throws IOException {
        ProtoProperty property = requireOwner();
        if (firstElement || valuePending) {
            input.skip(currentWireType, limit);
            firstElement = false;
            valuePending = false;
        }
        skipFollowingOccurrences(property);
    }

    private SerdeException unsupportedSchemaFree(String what) {
        return new SerdeException("Protocol Buffers cannot be read without a schema, so " + what
            + " cannot be decoded. Declare a concrete @Serdeable type with @ProtoField numbers instead.");
    }

    private <T> Object decodeResolvedValue(DecoderContext context, Argument<T> argument) throws IOException {
        Deserializer<? extends T> deserializer = context.findDeserializer(argument)
            .createSpecific(context, argument);
        return deserializer.deserialize(this, context, argument);
    }

    /**
     * A running total of the bytes spent normalizing a single payload's messages.
     *
     * <p>Shared by every decoder reading one payload, so the cost is bounded across the whole tree
     * rather than per message. Without it, nesting multiplies the retained bytes by the depth
     * limit, and a payload of a megabyte can hold three orders of magnitude more heap than its
     * size suggests.</p>
     */
    /**
     * The collaborators that belong to one payload rather than to one structure within it, carried
     * together so every nested decoder shares the same context and the same budget.
     *
     * @param decoderContext The context used to resolve deserializers for nested values
     * @param budget         The running total of bytes spent normalizing this payload's messages
     */
    private record PayloadScope(DecoderContext decoderContext, NormalizationBudget budget) {
    }

    private static final class NormalizationBudget {
        private final long limit;
        private long spent;

        private NormalizationBudget(long limit) {
            this.limit = limit;
        }

        private void charge(int bytes) throws SerdeException {
            spent += bytes;
            if (spent > limit) {
                throw new SerdeException("This protobuf payload expands to more than " + limit
                    + " bytes of nested messages, which is far more than its own size. Refusing to"
                    + " continue: a payload this deeply nested is a denial of service rather than data.");
            }
        }
    }

    private enum Kind {
        ROOT,
        MESSAGE,
        REPEATED,
        PACKED,
        BYTES
    }
}
