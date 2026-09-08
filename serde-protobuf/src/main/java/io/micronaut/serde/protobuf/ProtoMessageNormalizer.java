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

import io.micronaut.serde.protobuf.wire.ProtoInput;
import io.micronaut.serde.protobuf.wire.ProtoOutput;
import io.micronaut.serde.protobuf.wire.ProtoWire;

import java.io.IOException;

/**
 * Rewrites one message into the unique-property shape expected by the serde object decoder.
 *
 * <p>Protobuf permits arbitrary field order and duplicate occurrences. Repeated values are
 * concatenated, scalar duplicates use the last value, and duplicate embedded messages merge. Doing
 * that once at the message boundary keeps the regular decoder cursor simple and deterministic.</p>
 */
final class ProtoMessageNormalizer {

    private ProtoMessageNormalizer() {
    }

    static byte[] normalize(byte[] source, int start, int limit, ProtoSchema schema) throws IOException {
        var input = new ProtoInput(source);
        input.position(start);
        // left null until a slot is actually mentioned: a wide schema would otherwise allocate an
        // accumulator and its buffer for every declared field of every message, present or not
        var fields = new FieldAccumulator[schema.slotCount()];
        while (input.position() < limit) {
            accumulateField(source, input, limit, schema, fields);
        }
        return emit(source, start, limit, schema, fields);
    }

    /**
     * Read one field and fold it into the accumulator for its slot.
     */
    private static void accumulateField(byte[] source,
                                        ProtoInput input,
                                        int limit,
                                        ProtoSchema schema,
                                        FieldAccumulator[] fields) throws IOException {
        int tag = input.readTag();
        int fieldNumber = ProtoWire.fieldNumber(tag);
        int wireType = ProtoWire.wireType(tag);
        int valueStart = input.position();
        int payloadStart = -1;
        if (wireType == ProtoWire.LENGTH_DELIMITED) {
            int length = input.readLength(limit);
            payloadStart = input.position();
            input.position(payloadStart + length);
        } else {
            input.skip(wireType, limit);
        }
        int valueEnd = input.position();

        int slot = schema.slotOf(fieldNumber);
        if (slot < 0) {
            // a field this version does not know about: skipping keeps readers forward compatible
            return;
        }
        ProtoProperty property = schema.propertyAt(slot);
        if (property.repeatedField() && property.packableElement()) {
            accumulatePackable(source, property, accumulator(fields, slot), wireType, valueStart, payloadStart, valueEnd);
            return;
        }
        // protobuf treats a wire type that disagrees with the field as an unknown field. That
        // only holds where the schema knows the wire type; an opaque property is serialized by
        // a serde of its own choosing, so the payload is taken at its word instead.
        if (!property.opaqueWireType() && wireType != property.wireType()) {
            return;
        }
        FieldAccumulator field = accumulator(fields, slot);
        if (property.repeatedField()) {
            field.combined.writeTag(ProtoWire.tag(property.number(), wireType));
            field.combined.writeRaw(source, valueStart, valueEnd - valueStart);
        } else if (property.messageField()) {
            field.seen = true;
            field.combined.writeRaw(source, payloadStart, valueEnd - payloadStart);
        } else {
            field.seen = true;
            field.wireType = wireType;
            field.valueStart = valueStart;
            field.valueEnd = valueEnd;
        }
    }

    /**
     * A repeated scalar may arrive packed into one run or as separately tagged values, and the two
     * shapes may be mixed within a message. Either way the values are concatenated.
     */
    /**
     * The accumulator for a slot, created on first use.
     *
     * <p>Allocating one per schema slot per message meant a wide schema paid for every declared
     * field whether the payload mentioned it or not, buffer included.</p>
     */
    private static FieldAccumulator accumulator(FieldAccumulator[] fields, int slot) {
        FieldAccumulator field = fields[slot];
        if (field == null) {
            field = new FieldAccumulator();
            fields[slot] = field;
        }
        return field;
    }

    private static void accumulatePackable(byte[] source,
                                           ProtoProperty property,
                                           FieldAccumulator field,
                                           int wireType,
                                           int valueStart,
                                           int payloadStart,
                                           int valueEnd) {
        if (wireType == ProtoWire.LENGTH_DELIMITED) {
            field.combined.writeRaw(source, payloadStart, valueEnd - payloadStart);
        } else if (wireType == property.wireType()) {
            field.combined.writeRaw(source, valueStart, valueEnd - valueStart);
        }
    }

    /**
     * Write the accumulated fields back out, each once and in schema order.
     */
    private static byte[] emit(byte[] source,
                               int start,
                               int limit,
                               ProtoSchema schema,
                               FieldAccumulator[] fields) {
        var normalized = new ProtoOutput(Math.max(64, limit - start));
        for (int slot = 0; slot < fields.length; slot++) {
            FieldAccumulator field = fields[slot];
            if (field == null) {
                // the payload never mentioned this field
                continue;
            }
            ProtoProperty property = schema.propertyAt(slot);
            if (property.repeatedField()) {
                emitRepeated(normalized, property, field);
            } else if (property.messageField()) {
                if (field.seen) {
                    normalized.writeTag(property.lengthTag());
                    normalized.writeLengthDelimited(field.combined);
                }
            } else if (field.seen) {
                normalized.writeTag(ProtoWire.tag(property.number(), field.wireType));
                normalized.writeRaw(source, field.valueStart, field.valueEnd - field.valueStart);
            }
        }
        return normalized.toByteArray();
    }

    private static void emitRepeated(ProtoOutput normalized, ProtoProperty property, FieldAccumulator field) {
        if (field.combined.size() == 0) {
            return;
        }
        if (property.packableElement()) {
            normalized.writeTag(property.lengthTag());
            normalized.writeLengthDelimited(field.combined);
        } else {
            // each element already carries its own tag
            normalized.writeRaw(field.combined);
        }
    }

    private static final class FieldAccumulator {
        private final ProtoOutput combined = new ProtoOutput();
        private boolean seen;
        private int wireType;
        private int valueStart;
        private int valueEnd;
    }
}
