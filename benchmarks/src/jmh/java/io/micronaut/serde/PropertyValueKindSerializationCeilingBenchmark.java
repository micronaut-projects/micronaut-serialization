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
package io.micronaut.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.jackson.JacksonEncoder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.ByteArrayBuilder;

import java.io.IOException;
import java.util.List;

/**
 * Isolates Jackson generator, Micronaut encoder, and key-writing overhead for primitive field output.
 */
@SuppressWarnings("deprecation")
public class PropertyValueKindSerializationCeilingBenchmark {

    private static final List<String> KEY_NAMES = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.serialize());
    }

    @State(Scope.Thread)
    public static class Holder {

        @Param({
            "RAW_SERIALIZABLE_KEYS",
            "RAW_CONTEXT_SERIALIZABLE_KEYS",
            "RAW_STRING_KEYS",
            "ENCODER_KEYS",
            "ENCODER_CONTEXT_KEYS",
            "ENCODER_STRING_KEYS"
        })
        Path path = Path.RAW_SERIALIZABLE_KEYS;

        private final JsonFactory jsonFactory = new JsonFactory();
        private SerializableString[] serializableKeys;
        private Keys keys;

        @Setup
        public void setUp() {
            serializableKeys = new SerializableString[KEY_NAMES.size()];
            for (int i = 0; i < KEY_NAMES.size(); i++) {
                serializableKeys[i] = new SerializedString(KEY_NAMES.get(i));
            }
            keys = Keys.create(KEY_NAMES);
        }

        byte[] serialize() throws IOException {
            return switch (path) {
                case RAW_SERIALIZABLE_KEYS -> writeRawSerializableKeys();
                case RAW_CONTEXT_SERIALIZABLE_KEYS -> writeRawContextSerializableKeys();
                case RAW_STRING_KEYS -> writeRawStringKeys();
                case ENCODER_KEYS -> writeEncoderKeys();
                case ENCODER_CONTEXT_KEYS -> writeEncoderContextKeys();
                case ENCODER_STRING_KEYS -> writeEncoderStringKeys();
            };
        }

        private byte[] writeRawSerializableKeys() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    generator.writeStartObject();
                    generator.writeName(serializableKeys[0]);
                    generator.writeNumber(1000);
                    generator.writeName(serializableKeys[1]);
                    generator.writeNumber(9_000_000_123L);
                    generator.writeName(serializableKeys[2]);
                    generator.writeBoolean(true);
                    generator.writeName(serializableKeys[3]);
                    generator.writeNumber(123.456D);
                    generator.writeName(serializableKeys[4]);
                    generator.writeNumber(2000);
                    generator.writeName(serializableKeys[5]);
                    generator.writeNumber(9_000_000_456L);
                    generator.writeName(serializableKeys[6]);
                    generator.writeBoolean(false);
                    generator.writeName(serializableKeys[7]);
                    generator.writeNumber(789.123D);
                    generator.writeName(serializableKeys[8]);
                    generator.writeNumber(3000);
                    generator.writeName(serializableKeys[9]);
                    generator.writeNumber(9_000_000_789L);
                    generator.writeEndObject();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeRawContextSerializableKeys() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), output, JsonEncoding.UTF8)) {
                    generator.writeStartObject();
                    generator.writeName(serializableKeys[0]);
                    generator.writeNumber(1000);
                    generator.writeName(serializableKeys[1]);
                    generator.writeNumber(9_000_000_123L);
                    generator.writeName(serializableKeys[2]);
                    generator.writeBoolean(true);
                    generator.writeName(serializableKeys[3]);
                    generator.writeNumber(123.456D);
                    generator.writeName(serializableKeys[4]);
                    generator.writeNumber(2000);
                    generator.writeName(serializableKeys[5]);
                    generator.writeNumber(9_000_000_456L);
                    generator.writeName(serializableKeys[6]);
                    generator.writeBoolean(false);
                    generator.writeName(serializableKeys[7]);
                    generator.writeNumber(789.123D);
                    generator.writeName(serializableKeys[8]);
                    generator.writeNumber(3000);
                    generator.writeName(serializableKeys[9]);
                    generator.writeNumber(9_000_000_789L);
                    generator.writeEndObject();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeRawStringKeys() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    generator.writeStartObject();
                    generator.writeName(KEY_NAMES.get(0));
                    generator.writeNumber(1000);
                    generator.writeName(KEY_NAMES.get(1));
                    generator.writeNumber(9_000_000_123L);
                    generator.writeName(KEY_NAMES.get(2));
                    generator.writeBoolean(true);
                    generator.writeName(KEY_NAMES.get(3));
                    generator.writeNumber(123.456D);
                    generator.writeName(KEY_NAMES.get(4));
                    generator.writeNumber(2000);
                    generator.writeName(KEY_NAMES.get(5));
                    generator.writeNumber(9_000_000_456L);
                    generator.writeName(KEY_NAMES.get(6));
                    generator.writeBoolean(false);
                    generator.writeName(KEY_NAMES.get(7));
                    generator.writeNumber(789.123D);
                    generator.writeName(KEY_NAMES.get(8));
                    generator.writeNumber(3000);
                    generator.writeName(KEY_NAMES.get(9));
                    generator.writeNumber(9_000_000_789L);
                    generator.writeEndObject();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeEncoderKeys() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    Encoder encoder = JacksonEncoder.create(generator);
                    KeysAwareEncoder objectEncoder = KeysAwareEncoder.of(encoder.encodeObject(Argument.OBJECT_ARGUMENT));
                    objectEncoder.encodeKey(keys, 0);
                    objectEncoder.encodeInt(1000);
                    objectEncoder.encodeKey(keys, 1);
                    objectEncoder.encodeLong(9_000_000_123L);
                    objectEncoder.encodeKey(keys, 2);
                    objectEncoder.encodeBoolean(true);
                    objectEncoder.encodeKey(keys, 3);
                    objectEncoder.encodeDouble(123.456D);
                    objectEncoder.encodeKey(keys, 4);
                    objectEncoder.encodeInt(2000);
                    objectEncoder.encodeKey(keys, 5);
                    objectEncoder.encodeLong(9_000_000_456L);
                    objectEncoder.encodeKey(keys, 6);
                    objectEncoder.encodeBoolean(false);
                    objectEncoder.encodeKey(keys, 7);
                    objectEncoder.encodeDouble(789.123D);
                    objectEncoder.encodeKey(keys, 8);
                    objectEncoder.encodeInt(3000);
                    objectEncoder.encodeKey(keys, 9);
                    objectEncoder.encodeLong(9_000_000_789L);
                    objectEncoder.finishStructure();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeEncoderContextKeys() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), output, JsonEncoding.UTF8)) {
                    Encoder encoder = JacksonEncoder.create(generator);
                    KeysAwareEncoder objectEncoder = KeysAwareEncoder.of(encoder.encodeObject(Argument.OBJECT_ARGUMENT));
                    objectEncoder.encodeKey(keys, 0);
                    objectEncoder.encodeInt(1000);
                    objectEncoder.encodeKey(keys, 1);
                    objectEncoder.encodeLong(9_000_000_123L);
                    objectEncoder.encodeKey(keys, 2);
                    objectEncoder.encodeBoolean(true);
                    objectEncoder.encodeKey(keys, 3);
                    objectEncoder.encodeDouble(123.456D);
                    objectEncoder.encodeKey(keys, 4);
                    objectEncoder.encodeInt(2000);
                    objectEncoder.encodeKey(keys, 5);
                    objectEncoder.encodeLong(9_000_000_456L);
                    objectEncoder.encodeKey(keys, 6);
                    objectEncoder.encodeBoolean(false);
                    objectEncoder.encodeKey(keys, 7);
                    objectEncoder.encodeDouble(789.123D);
                    objectEncoder.encodeKey(keys, 8);
                    objectEncoder.encodeInt(3000);
                    objectEncoder.encodeKey(keys, 9);
                    objectEncoder.encodeLong(9_000_000_789L);
                    objectEncoder.finishStructure();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeEncoderStringKeys() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    Encoder encoder = JacksonEncoder.create(generator);
                    KeysAwareEncoder objectEncoder = KeysAwareEncoder.of(encoder.encodeObject(Argument.OBJECT_ARGUMENT));
                    objectEncoder.encodeKey(KEY_NAMES.get(0));
                    objectEncoder.encodeInt(1000);
                    objectEncoder.encodeKey(KEY_NAMES.get(1));
                    objectEncoder.encodeLong(9_000_000_123L);
                    objectEncoder.encodeKey(KEY_NAMES.get(2));
                    objectEncoder.encodeBoolean(true);
                    objectEncoder.encodeKey(KEY_NAMES.get(3));
                    objectEncoder.encodeDouble(123.456D);
                    objectEncoder.encodeKey(KEY_NAMES.get(4));
                    objectEncoder.encodeInt(2000);
                    objectEncoder.encodeKey(KEY_NAMES.get(5));
                    objectEncoder.encodeLong(9_000_000_456L);
                    objectEncoder.encodeKey(KEY_NAMES.get(6));
                    objectEncoder.encodeBoolean(false);
                    objectEncoder.encodeKey(KEY_NAMES.get(7));
                    objectEncoder.encodeDouble(789.123D);
                    objectEncoder.encodeKey(KEY_NAMES.get(8));
                    objectEncoder.encodeInt(3000);
                    objectEncoder.encodeKey(KEY_NAMES.get(9));
                    objectEncoder.encodeLong(9_000_000_789L);
                    objectEncoder.finishStructure();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }
    }

    public enum Path {
        RAW_SERIALIZABLE_KEYS,
        RAW_CONTEXT_SERIALIZABLE_KEYS,
        RAW_STRING_KEYS,
        ENCODER_KEYS,
        ENCODER_CONTEXT_KEYS,
        ENCODER_STRING_KEYS
    }
}
