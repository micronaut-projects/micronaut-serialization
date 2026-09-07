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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.protobuf.wire.ProtoOutput;
import io.micronaut.serde.protobuf.wire.ProtoWire;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Protocol Buffers implementation of {@link Encoder}.
 *
 * <p>Two things about the wire format shape this class:</p>
 *
 * <ul>
 *   <li><b>Tags are written lazily.</b> Serde encodes the key before it knows whether the value is
 *   null, but protobuf has no null &mdash; an absent field is simply not on the wire. So
 *   {@link #encodeKey} only records the property, and the tag is written when a value actually
 *   arrives. {@link #encodeNull()} discards the pending property instead.</li>
 *   <li><b>Nested structures buffer.</b> A nested message is length-prefixed, and its length is not
 *   known until the structure is finished, so each nested message and each repeated field writes
 *   into its own buffer and is copied into its parent by {@link #finishStructure()}. Those buffers
 *   are pooled on the root encoder and reused for the rest of the message.</li>
 * </ul>
 *
 * @since 3.2
 */
@Internal
public final class ProtobufEncoder extends LimitingStream implements KeysAwareEncoder {

    private static final int INITIAL_POOL_SIZE = 8;

    private final ProtobufEncoder root;
    private final @Nullable ProtobufEncoder parent;
    private final ProtoOutput out;
    private final Kind kind;
    private final @Nullable ProtoSchema schema;
    private final @Nullable ProtoProperty owner;

    private @Nullable ProtoProperty pending;

    private boolean packed;
    private boolean packedDecided;

    private @Nullable Keys currentKeys;
    private @Nullable ProtoProperty @Nullable [] currentKeyProperties;

    private @Nullable ProtoOutput @Nullable [] pool;
    private int pooled;

    /**
     * Create a root encoder. A protobuf payload is a single message, so the root itself writes
     * nothing &mdash; it exists to receive the top-level {@link #encodeObject(Argument)} call.
     *
     * @param remainingLimits The nesting limits
     */
    public ProtobufEncoder(RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.root = this;
        this.parent = null;
        this.out = new ProtoOutput(128);
        this.kind = Kind.ROOT;
        this.schema = null;
        this.owner = null;
    }

    private ProtobufEncoder(ProtobufEncoder parent,
                            ProtoOutput out,
                            Kind kind,
                            @Nullable ProtoSchema schema,
                            @Nullable ProtoProperty owner,
                            RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.root = parent.root;
        this.parent = parent;
        this.out = out;
        this.kind = kind;
        this.schema = schema;
        this.owner = owner;
    }

    /**
     * Returns the encoded message.
     *
     * @return The bytes
     */
    public byte[] toByteArray() {
        return out.toByteArray();
    }

    /**
     * Write the encoded message to a stream.
     *
     * @param outputStream The stream
     * @throws IOException If writing fails
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        out.writeTo(outputStream);
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        ProtoSchema childSchema = ProtoSchema.of(type);
        return switch (kind) {
            // the top-level message is the payload itself, so it carries no tag and no length prefix
            case ROOT -> new ProtobufEncoder(this, out, Kind.MESSAGE, childSchema, null, childLimits());
            case MESSAGE -> new ProtobufEncoder(this, borrow(), Kind.MESSAGE, childSchema, takePending(), childLimits());
            case REPEATED -> new ProtobufEncoder(this, borrow(), Kind.MESSAGE, childSchema, owner, childLimits());
            case BYTES -> throw new SerdeException("A protobuf bytes field cannot contain messages");
        };
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        return switch (kind) {
            case ROOT -> throw new SerdeException("A protobuf payload must be a message. Wrap the collection in a type annotated with @Serdeable.");
            case MESSAGE -> {
                ProtoProperty property = takePending();
                // a byte[] written element by element is still a bytes field, not a repeated one
                Kind childKind = property.bytesField() ? Kind.BYTES : Kind.REPEATED;
                yield new ProtobufEncoder(this, borrow(), childKind, null, property, childLimits());
            }
            case REPEATED -> {
                ProtoProperty property = requireOwner();
                if (!property.repeatedBytesField()) {
                    throw new SerdeException("Protocol Buffers has no nested repeated fields. Wrap the inner collection in a message type.");
                }
                yield new ProtobufEncoder(this, borrow(), Kind.BYTES, null, property, childLimits());
            }
            case BYTES -> throw new SerdeException("A protobuf bytes field cannot contain arrays");
        };
    }

    @Override
    public void finishStructure() throws IOException {
        ProtobufEncoder target = parent;
        if (target == null || kind == Kind.ROOT) {
            throw new IllegalStateException("Not in a structure");
        }
        switch (kind) {
            case MESSAGE -> finishNestedMessage(target);
            case BYTES -> finishBytesField(target);
            case REPEATED -> finishRepeatedField(target);
            default -> throw new IllegalStateException("Unknown structure kind " + kind);
        }
    }

    private void finishNestedMessage(ProtobufEncoder target) throws IOException {
        if (owner == null) {
            // the top-level message shares the root buffer and carries no tag or length prefix
            return;
        }
        target.beforeElement(false);
        target.out.writeTag(owner.lengthTag());
        target.out.writeLengthDelimited(out);
        target.recycle(out);
    }

    private void finishBytesField(ProtobufEncoder target) {
        ProtoProperty property = requireOwner();
        if (out.size() != 0 || property.explicitPresence() || target.kind == Kind.REPEATED) {
            target.out.writeTag(property.lengthTag());
            target.out.writeLengthDelimited(out);
        }
        target.recycle(out);
    }

    private void finishRepeatedField(ProtobufEncoder target) {
        // an empty repeated field is simply absent from the payload
        if (out.size() == 0) {
            target.recycle(out);
            return;
        }
        if (packed) {
            target.out.writeTag(requireOwner().lengthTag());
            target.out.writeLengthDelimited(out);
        } else {
            // each element already carries its own tag
            target.out.writeRaw(out);
        }
        target.recycle(out);
    }

    @Override
    public void encodeKey(String key) throws IOException {
        ProtoSchema currentSchema = schema;
        if (kind != Kind.MESSAGE || currentSchema == null) {
            throw new SerdeException("A protobuf property key can only be encoded inside a message");
        }
        ProtoProperty property = currentSchema.byName(key);
        if (property == null) {
            throw unmapped(key);
        }
        pending = property;
    }

    @Override
    public void encodeKey(Keys keys, int index) throws IOException {
        ProtoSchema currentSchema = schema;
        if (kind != Kind.MESSAGE || currentSchema == null) {
            throw new SerdeException("A protobuf property key can only be encoded inside a message");
        }
        @Nullable ProtoProperty[] properties = currentKeyProperties;
        if (keys != currentKeys || properties == null) {
            properties = currentSchema.byKeyIndex(keys);
            currentKeys = keys;
            currentKeyProperties = properties;
        }
        ProtoProperty property = properties[index];
        if (property == null) {
            throw unmapped(ProtoSchema.keyNames(keys)[index]);
        }
        pending = property;
    }

    @Override
    public void encodeString(String value) throws IOException {
        ProtoProperty property = startValue(false);
        if (omitDefault(property, value.isEmpty())) {
            return;
        }
        tag(property.lengthTag());
        out.writeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        ProtoProperty property = startValue(true);
        if (omitDefault(property, !value)) {
            return;
        }
        tag(property.varintTag());
        out.writeVarint(value ? 1 : 0);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        if (kind == Kind.BYTES) {
            out.writeByte(value);
            return;
        }
        encodeInt(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        encodeInt(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        ProtoProperty property = startValue(true);
        if (omitDefault(property, value == 0)) {
            return;
        }
        tag(property.varintTag());
        out.writeVarint(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        ProtoProperty property = startValue(true);
        if (omitDefault(property, value == 0)) {
            return;
        }
        switch (property.intKind()) {
            case ProtoProperty.KIND_ZIGZAG -> {
                tag(property.varintTag());
                out.writeVarint(Integer.toUnsignedLong(ProtoWire.zigZagEncode32(value)));
            }
            case ProtoProperty.KIND_UINT32 -> {
                tag(property.varintTag());
                out.writeVarint(Integer.toUnsignedLong(value));
            }
            case ProtoProperty.KIND_FIXED32 -> {
                tag(property.fixed32Tag());
                out.writeFixed32(value);
            }
            case ProtoProperty.KIND_FIXED64 -> {
                tag(property.fixed64Tag());
                out.writeFixed64(value);
            }
            default -> {
                tag(property.varintTag());
                out.writeInt32(value);
            }
        }
    }

    @Override
    public void encodeLong(long value) throws IOException {
        ProtoProperty property = startValue(true);
        if (omitDefault(property, value == 0)) {
            return;
        }
        switch (property.longKind()) {
            case ProtoProperty.KIND_ZIGZAG -> {
                tag(property.varintTag());
                out.writeVarint(ProtoWire.zigZagEncode64(value));
            }
            case ProtoProperty.KIND_FIXED32 -> {
                tag(property.fixed32Tag());
                out.writeFixed32((int) value);
            }
            case ProtoProperty.KIND_FIXED64 -> {
                tag(property.fixed64Tag());
                out.writeFixed64(value);
            }
            default -> {
                tag(property.varintTag());
                out.writeVarint(value);
            }
        }
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        ProtoProperty property = startValue(true);
        if (omitDefault(property, Float.floatToRawIntBits(value) == 0)) {
            return;
        }
        tag(property.fixed32Tag());
        out.writeFixed32(Float.floatToRawIntBits(value));
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        ProtoProperty property = startValue(true);
        if (omitDefault(property, Double.doubleToRawLongBits(value) == 0)) {
            return;
        }
        tag(property.fixed64Tag());
        out.writeFixed64(Double.doubleToRawLongBits(value));
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        encodeString(value.toString());
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        encodeString(value.toString());
    }

    @Override
    public void encodeBinary(byte[] data) throws IOException {
        ProtoProperty property = startValue(false);
        if (omitDefault(property, data.length == 0)) {
            return;
        }
        tag(property.lengthTag());
        out.writeLengthDelimited(data);
    }

    @Override
    public void encodeNull() throws IOException {
        if (kind == Kind.REPEATED || kind == Kind.BYTES) {
            throw new SerdeException("Protocol Buffers cannot represent a null element in the repeated field ["
                + (owner == null ? "?" : owner.name()) + "]");
        }
        // an absent protobuf field is encoded by writing nothing at all, so drop the pending tag
        pending = null;
    }

    @Override
    public String currentPath() {
        ProtoProperty property = pending != null ? pending : owner;
        return property == null ? "" : property.name();
    }

    private ProtoOutput borrow() {
        ProtobufEncoder holder = root;
        @Nullable ProtoOutput[] buffers = holder.pool;
        if (buffers != null && holder.pooled > 0) {
            ProtoOutput reused = Objects.requireNonNull(buffers[--holder.pooled]);
            buffers[holder.pooled] = null;
            reused.reset();
            return reused;
        }
        return new ProtoOutput();
    }

    private void recycle(ProtoOutput buffer) {
        ProtobufEncoder holder = root;
        @Nullable ProtoOutput[] buffers = holder.pool;
        if (buffers == null) {
            buffers = new ProtoOutput[INITIAL_POOL_SIZE];
            holder.pool = buffers;
        }
        if (holder.pooled < buffers.length) {
            buffers[holder.pooled++] = buffer;
        }
    }

    private ProtoProperty requireOwner() {
        ProtoProperty property = owner;
        if (property == null) {
            throw new IllegalStateException("Repeated protobuf field has no owning property");
        }
        return property;
    }

    private ProtoProperty takePending() throws IOException {
        ProtoProperty property = pending;
        if (property == null) {
            throw new SerdeException("A protobuf value was encoded without a preceding property key");
        }
        pending = null;
        return property;
    }

    private ProtoProperty startValue(boolean packable) throws IOException {
        if (kind == Kind.REPEATED) {
            beforeElement(packable);
            return requireOwner();
        }
        if (kind != Kind.MESSAGE) {
            throw new SerdeException("A protobuf payload must be a message. Scalar values cannot be encoded at the top level.");
        }
        return takePending();
    }

    private boolean omitDefault(ProtoProperty property, boolean defaultValue) {
        return kind == Kind.MESSAGE && defaultValue && !property.explicitPresence();
    }

    private void beforeElement(boolean packable) throws IOException {
        if (kind != Kind.REPEATED) {
            return;
        }
        if (!packedDecided) {
            packed = packable;
            packedDecided = true;
        } else if (packed != packable) {
            throw new SerdeException("The repeated field [" + (owner == null ? "?" : owner.name())
                + "] mixes packed and length-delimited elements, which protobuf does not allow");
        }
    }

    private void tag(int tag) {
        if (packed) {
            // packed elements share one tag, written by finishStructure
            return;
        }
        out.writeTag(tag);
    }

    private SerdeException unmapped(String key) {
        return new SerdeException("No @ProtoField mapping for property [" + key + "] of message ["
            + (schema == null ? "?" : schema.messageType().getName()) + "]");
    }

    private enum Kind {
        ROOT,
        MESSAGE,
        REPEATED,
        BYTES
    }
}
