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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.jackson.JacksonEncoder;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.serializers.ErrorCatchingSerializer;
import io.micronaut.serde.util.GeneratedSerdeExceptionUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Attributes generated primitive serialization cost to mapper, encoder, generator, and writer lifecycle.
 */
public class PropertyValueKindSerializationLifecycleBenchmark {

    private static final Argument<PropertyValueKindBenchmark.PrimitiveGetterSetterShape> TYPE =
        Argument.of(PropertyValueKindBenchmark.PrimitiveGetterSetterShape.class);
    private static final List<String> KEY_NAMES = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
    private static final Argument<?> ARGUMENT_0 = Argument.INT.withName("a");
    private static final Argument<?> ARGUMENT_1 = Argument.LONG.withName("b");
    private static final Argument<?> ARGUMENT_2 = Argument.BOOLEAN.withName("c");
    private static final Argument<?> ARGUMENT_3 = Argument.DOUBLE.withName("d");
    private static final Argument<?> ARGUMENT_4 = Argument.INT.withName("e");
    private static final Argument<?> ARGUMENT_5 = Argument.LONG.withName("f");
    private static final Argument<?> ARGUMENT_6 = Argument.BOOLEAN.withName("g");
    private static final Argument<?> ARGUMENT_7 = Argument.DOUBLE.withName("h");
    private static final Argument<?> ARGUMENT_8 = Argument.INT.withName("i");
    private static final Argument<?> ARGUMENT_9 = Argument.LONG.withName("j");

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.serialize());
    }

    @State(Scope.Thread)
    public static class Holder {

        @Param({
            "SERDE_MAPPER",
            "SERDE_DIRECT_SERIALIZER",
            "SERDE_DIRECT_OBJECT_SERIALIZER",
            "ENCODER_VALUE_GETTERS",
            "ENCODER_VALUE_GETTERS_TRY_CATCH",
            "RAW_VALUE_GETTERS",
            "BLACKBIRD_MAPPER",
            "BLACKBIRD_OBJECT_WRITER"
        })
        Path path = Path.SERDE_MAPPER;

        private final JsonFactory jsonFactory = JsonFactory.builder()
            .recyclerPool(JsonRecyclerPools.threadLocalPool())
            .build();
        private final SerializableString[] serializableKeys = new SerializableString[KEY_NAMES.size()];
        private final Keys keys = Keys.create(KEY_NAMES);
        private final PropertyValueKindBenchmark.PrimitiveGetterSetterShape value = new PropertyValueKindBenchmark.PrimitiveGetterSetterShape();

        private ApplicationContext context;
        private JsonMapper serdeMapper;
        private Serializer.EncoderContext encoderContext;
        private Serializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape> serializer;
        private ObjectSerializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape> objectSerializer;
        private ObjectMapper blackbirdMapper;
        private ObjectWriter blackbirdWriter;

        @Setup
        public void setUp() throws Exception {
            for (int i = 0; i < KEY_NAMES.size(); i++) {
                serializableKeys[i] = new SerializedString(KEY_NAMES.get(i));
            }
            setValues(value);
            if (path.usesSerde()) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS"
                ));
                serdeMapper = context.getBean(JacksonJsonMapper.class).createSpecific(TYPE);
                SerdeRegistry registry = context.getBean(SerdeRegistry.class);
                encoderContext = registry.newEncoderContext(Object.class);
                serializer = createSerializer(registry);
                objectSerializer = createObjectSerializer(serializer);
            }
            if (path.usesBlackbird()) {
                blackbirdMapper = tools.jackson.databind.json.JsonMapper.builder()
                    .addModule(new BlackbirdModule())
                    .build();
                blackbirdWriter = blackbirdMapper.writerFor(PropertyValueKindBenchmark.PrimitiveGetterSetterShape.class);
            }
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }

        byte[] serialize() throws IOException {
            return switch (path) {
                case SERDE_MAPPER -> serdeMapper.writeValueAsBytes(TYPE, value);
                case SERDE_DIRECT_SERIALIZER -> writeWithSerializer();
                case SERDE_DIRECT_OBJECT_SERIALIZER -> writeWithObjectSerializer();
                case ENCODER_VALUE_GETTERS -> writeEncoderValueGetters();
                case ENCODER_VALUE_GETTERS_TRY_CATCH -> writeEncoderValueGettersTryCatch();
                case RAW_VALUE_GETTERS -> writeRawValueGetters();
                case BLACKBIRD_MAPPER -> blackbirdMapper.writeValueAsBytes(value);
                case BLACKBIRD_OBJECT_WRITER -> blackbirdWriter.writeValueAsBytes(value);
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Serializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape> createSerializer(SerdeRegistry registry) throws SerdeException {
            return (Serializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape>) registry.findSerializer(TYPE)
                .createSpecific(encoderContext, (Argument) TYPE);
        }

        @SuppressWarnings("unchecked")
        private ObjectSerializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape> createObjectSerializer(
            Serializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape> serializer) {
            Serializer<?> unwrappedSerializer = serializer instanceof ErrorCatchingSerializer<?> errorCatchingSerializer
                ? errorCatchingSerializer.getSerializer()
                : serializer;
            if (!(unwrappedSerializer instanceof ObjectSerializer<?> objectSerializer)) {
                throw new IllegalStateException("Expected object serializer but found " + unwrappedSerializer.getClass().getName());
            }
            return (ObjectSerializer<PropertyValueKindBenchmark.PrimitiveGetterSetterShape>) objectSerializer;
        }

        private byte[] writeWithSerializer() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    serializer.serialize(JacksonEncoder.create(generator), encoderContext, TYPE, value);
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeWithObjectSerializer() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    Encoder encoder = JacksonEncoder.create(generator);
                    Encoder objectEncoder = encoder.encodeObject(TYPE);
                    objectSerializer.serializeInto(objectEncoder, encoderContext, TYPE, value);
                    objectEncoder.finishStructure();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeEncoderValueGetters() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    Encoder encoder = JacksonEncoder.create(generator);
                    KeysAwareEncoder objectEncoder = KeysAwareEncoder.of(encoder.encodeObject(TYPE));
                    objectEncoder.encodeKey(keys, 0);
                    objectEncoder.encodeInt(value.getA());
                    objectEncoder.encodeKey(keys, 1);
                    objectEncoder.encodeLong(value.getB());
                    objectEncoder.encodeKey(keys, 2);
                    objectEncoder.encodeBoolean(value.isC());
                    objectEncoder.encodeKey(keys, 3);
                    objectEncoder.encodeDouble(value.getD());
                    objectEncoder.encodeKey(keys, 4);
                    objectEncoder.encodeInt(value.getE());
                    objectEncoder.encodeKey(keys, 5);
                    objectEncoder.encodeLong(value.getF());
                    objectEncoder.encodeKey(keys, 6);
                    objectEncoder.encodeBoolean(value.isG());
                    objectEncoder.encodeKey(keys, 7);
                    objectEncoder.encodeDouble(value.getH());
                    objectEncoder.encodeKey(keys, 8);
                    objectEncoder.encodeInt(value.getI());
                    objectEncoder.encodeKey(keys, 9);
                    objectEncoder.encodeLong(value.getJ());
                    objectEncoder.finishStructure();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeEncoderValueGettersTryCatch() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    Encoder encoder = JacksonEncoder.create(generator);
                    KeysAwareEncoder objectEncoder = KeysAwareEncoder.of(encoder.encodeObject(TYPE));
                    objectEncoder.encodeKey(keys, 0);
                    try {
                        objectEncoder.encodeInt(value.getA());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_0);
                    }
                    objectEncoder.encodeKey(keys, 1);
                    try {
                        objectEncoder.encodeLong(value.getB());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_1);
                    }
                    objectEncoder.encodeKey(keys, 2);
                    try {
                        objectEncoder.encodeBoolean(value.isC());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_2);
                    }
                    objectEncoder.encodeKey(keys, 3);
                    try {
                        objectEncoder.encodeDouble(value.getD());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_3);
                    }
                    objectEncoder.encodeKey(keys, 4);
                    try {
                        objectEncoder.encodeInt(value.getE());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_4);
                    }
                    objectEncoder.encodeKey(keys, 5);
                    try {
                        objectEncoder.encodeLong(value.getF());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_5);
                    }
                    objectEncoder.encodeKey(keys, 6);
                    try {
                        objectEncoder.encodeBoolean(value.isG());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_6);
                    }
                    objectEncoder.encodeKey(keys, 7);
                    try {
                        objectEncoder.encodeDouble(value.getH());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_7);
                    }
                    objectEncoder.encodeKey(keys, 8);
                    try {
                        objectEncoder.encodeInt(value.getI());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_8);
                    }
                    objectEncoder.encodeKey(keys, 9);
                    try {
                        objectEncoder.encodeLong(value.getJ());
                    } catch (Throwable e) {
                        throw GeneratedSerdeExceptionUtil.withPropertyPath(e, TYPE, ARGUMENT_9);
                    }
                    objectEncoder.finishStructure();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private byte[] writeRawValueGetters() throws IOException {
            BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
            try (ByteArrayBuilder output = new ByteArrayBuilder(bufferRecycler)) {
                try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
                    generator.writeStartObject();
                    generator.writeName(serializableKeys[0]);
                    generator.writeNumber(value.getA());
                    generator.writeName(serializableKeys[1]);
                    generator.writeNumber(value.getB());
                    generator.writeName(serializableKeys[2]);
                    generator.writeBoolean(value.isC());
                    generator.writeName(serializableKeys[3]);
                    generator.writeNumber(value.getD());
                    generator.writeName(serializableKeys[4]);
                    generator.writeNumber(value.getE());
                    generator.writeName(serializableKeys[5]);
                    generator.writeNumber(value.getF());
                    generator.writeName(serializableKeys[6]);
                    generator.writeBoolean(value.isG());
                    generator.writeName(serializableKeys[7]);
                    generator.writeNumber(value.getH());
                    generator.writeName(serializableKeys[8]);
                    generator.writeNumber(value.getI());
                    generator.writeName(serializableKeys[9]);
                    generator.writeNumber(value.getJ());
                    generator.writeEndObject();
                }
                return output.getClearAndRelease();
            } finally {
                bufferRecycler.releaseToPool();
            }
        }

        private static void setValues(PropertyValueKindBenchmark.PrimitiveGetterSetterShape value) {
            value.setA(1000);
            value.setB(9_000_000_123L);
            value.setC(true);
            value.setD(123.456D);
            value.setE(2000);
            value.setF(9_000_000_456L);
            value.setG(false);
            value.setH(789.123D);
            value.setI(3000);
            value.setJ(9_000_000_789L);
        }
    }

    public enum Path {
        SERDE_MAPPER,
        SERDE_DIRECT_SERIALIZER,
        SERDE_DIRECT_OBJECT_SERIALIZER,
        ENCODER_VALUE_GETTERS,
        ENCODER_VALUE_GETTERS_TRY_CATCH,
        RAW_VALUE_GETTERS,
        BLACKBIRD_MAPPER,
        BLACKBIRD_OBJECT_WRITER;

        boolean usesSerde() {
            return switch (this) {
                case SERDE_MAPPER, SERDE_DIRECT_SERIALIZER, SERDE_DIRECT_OBJECT_SERIALIZER -> true;
                case ENCODER_VALUE_GETTERS, ENCODER_VALUE_GETTERS_TRY_CATCH, RAW_VALUE_GETTERS, BLACKBIRD_MAPPER, BLACKBIRD_OBJECT_WRITER -> false;
            };
        }

        boolean usesBlackbird() {
            return switch (this) {
                case BLACKBIRD_MAPPER, BLACKBIRD_OBJECT_WRITER -> true;
                case SERDE_MAPPER, SERDE_DIRECT_SERIALIZER, SERDE_DIRECT_OBJECT_SERIALIZER, ENCODER_VALUE_GETTERS,
                    ENCODER_VALUE_GETTERS_TRY_CATCH, RAW_VALUE_GETTERS -> false;
            };
        }
    }
}
