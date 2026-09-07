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
        var fields = new FieldAccumulator[schema.slotCount()];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new FieldAccumulator();
        }

        while (input.position() < limit) {
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
                input.skip(fieldNumber, wireType, limit);
            }
            int valueEnd = input.position();

            int slot = schema.slotOf(fieldNumber);
            if (slot < 0) {
                continue;
            }
            ProtoProperty property = schema.propertyAt(slot);
            var field = fields[slot];
            if (property.repeatedField() && property.packableElement()) {
                if (wireType == ProtoWire.LENGTH_DELIMITED) {
                    field.combined.writeRaw(source, payloadStart, valueEnd - payloadStart);
                } else if (wireType == property.wireType()) {
                    field.combined.writeRaw(source, valueStart, valueEnd - valueStart);
                }
                continue;
            }
            // protobuf treats a wire type that disagrees with the field as an unknown field. That
            // only holds where the schema knows the wire type; an opaque property is serialized by
            // a serde of its own choosing, so the payload is taken at its word instead.
            if (!property.opaqueWireType() && wireType != property.wireType()) {
                continue;
            }
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

        var normalized = new ProtoOutput(Math.max(64, limit - start));
        for (int slot = 0; slot < fields.length; slot++) {
            ProtoProperty property = schema.propertyAt(slot);
            var field = fields[slot];
            if (property.repeatedField()) {
                if (field.combined.size() == 0) {
                    continue;
                }
                if (property.packableElement()) {
                    normalized.writeTag(property.lengthTag());
                    normalized.writeLengthDelimited(field.combined);
                } else {
                    normalized.writeRaw(field.combined);
                }
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

    private static final class FieldAccumulator {
        private final ProtoOutput combined = new ProtoOutput();
        private boolean seen;
        private int wireType;
        private int valueStart;
        private int valueEnd;
    }
}
