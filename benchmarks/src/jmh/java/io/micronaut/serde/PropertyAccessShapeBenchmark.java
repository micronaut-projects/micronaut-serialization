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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import io.micronaut.serde.support.deserializers.ErrorCatchingDeserializer;
import io.micronaut.serde.support.serializers.ErrorCatchingSerializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Compares property binding mechanics while keeping the JSON shape constant.
 */
public class PropertyAccessShapeBenchmark {

    private static final String JACKSON_DATABIND = "Jackson Databind";
    private static final String JACKSON_DATABIND_BLACKBIRD = "Jackson Databind Blackbird";
    private static final String SERDE_JACKSON_GENERATED = "Serde Jackson Generated";
    private static final String SERDE_JACKSON_RUNTIME = "Serde Jackson Runtime";
    private static final byte[] JSON = """
        {"name":"alpha","count":42,"id":9000000123,"active":true,"score":123.456,"code":"C-0123456789","enabled":false,"ratio":0.75,"size":128,"description":"description-value"}
        """.getBytes(StandardCharsets.UTF_8);

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.serialize());
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserialize(Holder holder) throws IOException {
        return holder.deserialize();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object roundTrip(Holder holder, Blackhole blackhole) throws IOException {
        byte[] bytes = holder.serialize();
        blackhole.consume(bytes);
        return holder.deserialize(bytes);
    }

    @State(Scope.Thread)
    public static class Holder {

        @Param({
            JACKSON_DATABIND,
            JACKSON_DATABIND_BLACKBIRD,
            SERDE_JACKSON_GENERATED,
            SERDE_JACKSON_RUNTIME
        })
        String stack = SERDE_JACKSON_GENERATED;

        @Param({
            "CONSTRUCTOR",
            "GETTER_SETTER",
            "FIELD"
        })
        Shape shape = Shape.CONSTRUCTOR;

        @Param({"true"})
        boolean failOnNullForPrimitives = true;

        private JsonMapper mapper;
        private ApplicationContext context;
        private ShapeCase<?> shapeCase;
        private Object value;

        @Setup
        public void setUp() throws Exception {
            shapeCase = shape.shapeCase();
            if (stack.equals(JACKSON_DATABIND)) {
                ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
                    .build();
                validateBlackbirdModule(objectMapper, false);
                mapper = new JacksonDatabindMapper(objectMapper);
            } else if (stack.equals(JACKSON_DATABIND_BLACKBIRD)) {
                ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
                    .addModule(new BlackbirdModule())
                    .build();
                validateBlackbirdModule(objectMapper, true);
                mapper = new JacksonDatabindMapper(objectMapper);
            } else if (stack.equals(SERDE_JACKSON_GENERATED)) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS",
                    "micronaut.serde.deserialization.fail-on-null-for-primitives", failOnNullForPrimitives
                ));
                mapper = context.getBean(JacksonJsonMapper.class);
                validateMicronautSerde(true);
            } else if (stack.equals(SERDE_JACKSON_RUNTIME)) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS",
                    "micronaut.serde.deserialization.fail-on-null-for-primitives", failOnNullForPrimitives,
                    "micronaut.serde.serialization.disable-generated-serializer", true,
                    "micronaut.serde.deserialization.disable-generated-deserializer", true
                ));
                mapper = context.getBean(JacksonJsonMapper.class);
                validateMicronautSerde(false);
            } else {
                throw new IllegalStateException("Unsupported stack: " + stack);
            }
            mapper = mapper.createSpecific(shapeCase.argument);
            value = deserialize(JSON);
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }

        byte[] serialize() throws IOException {
            return shapeCase.write(mapper, value);
        }

        Object deserialize() throws IOException {
            return deserialize(JSON);
        }

        Object deserialize(byte[] bytes) throws IOException {
            return shapeCase.read(mapper, bytes);
        }

        private void validateMicronautSerde(boolean generatedExpected) throws Exception {
            SerdeRegistry registry = context.getBean(SerdeRegistry.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            var serializer = registry.findSerializer((Argument) shapeCase.argument)
                .createSpecific(registry.newEncoderContext(Object.class), (Argument) shapeCase.argument);
            @SuppressWarnings({"unchecked", "rawtypes"})
            var deserializer = registry.findDeserializer((Argument) shapeCase.argument)
                .createSpecific(registry.newDecoderContext(Object.class), (Argument) shapeCase.argument);
            Object unwrappedSerializer = unwrapSerializer(serializer);
            Object unwrappedDeserializer = unwrapDeserializer(deserializer);
            if (generatedExpected) {
                validateGenerated("serializer", shapeCase.rawType, "Serializer", unwrappedSerializer);
                validateGenerated("deserializer", shapeCase.rawType, "Deserializer", unwrappedDeserializer);
            } else {
                validateRuntime("serializer", "io.micronaut.serde.support.serializers.SimpleObjectSerializer", unwrappedSerializer);
                String expectedDeserializer = shapeCase.rawType.isRecord()
                    ? "io.micronaut.serde.support.deserializers.SimpleRecordLikeObjectDeserializer"
                    : "io.micronaut.serde.support.deserializers.SimpleObjectDeserializer";
                validateRuntime("deserializer", expectedDeserializer, unwrappedDeserializer);
            }
        }

        private static Object unwrapSerializer(Serializer<?> serializer) {
            if (serializer instanceof ErrorCatchingSerializer<?> errorCatchingSerializer) {
                return errorCatchingSerializer.getSerializer();
            }
            return serializer;
        }

        private static Object unwrapDeserializer(Deserializer<?> deserializer) {
            if (deserializer instanceof ErrorCatchingDeserializer<?> errorCatchingDeserializer) {
                return errorCatchingDeserializer.getDeserializer();
            }
            return deserializer;
        }

        private static void validateGenerated(String role, Class<?> type, String suffix, Object serde) {
            String expected = generatedSerdeClassName(type, suffix);
            String className = serde.getClass().getName();
            if (!expected.equals(className)) {
                throw new IllegalStateException("Expected generated " + role + " " + expected + " but found " + className);
            }
        }

        private static void validateRuntime(String role, String expected, Object serde) {
            String className = serde.getClass().getName();
            if (!expected.equals(className)) {
                throw new IllegalStateException("Expected runtime " + role + " " + expected + " but found " + className);
            }
        }

        private static void validateBlackbirdModule(ObjectMapper objectMapper, boolean expected) {
            boolean present = objectMapper.registeredModules().stream()
                .anyMatch(BlackbirdModule.class::isInstance);
            if (present != expected) {
                throw new IllegalStateException("Expected Jackson Blackbird module present=" + expected + " but was " + present);
            }
        }

        private static String generatedSerdeClassName(Class<?> type, String suffix) {
            String packageName = type.getPackageName();
            String packagePrefix = packageName.isEmpty() ? "" : packageName + ".";
            String localName = type.getName();
            if (!packageName.isEmpty()) {
                localName = localName.substring(packageName.length() + 1);
            }
            localName = localName.replace('.', '_').replace('$', '_');
            return packagePrefix + "Serde" + localName + suffix;
        }
    }

    public enum Shape {
        CONSTRUCTOR(new ShapeCase<>(Argument.of(ConstructorShape.class))),
        GETTER_SETTER(new ShapeCase<>(Argument.of(GetterSetterShape.class))),
        FIELD(new ShapeCase<>(Argument.of(FieldShape.class)));

        private final ShapeCase<?> shapeCase;

        Shape(ShapeCase<?> shapeCase) {
            this.shapeCase = shapeCase;
        }

        ShapeCase<?> shapeCase() {
            return shapeCase;
        }
    }

    static final class ShapeCase<T> {
        final Argument<T> argument;
        final Class<T> rawType;

        ShapeCase(Argument<T> argument) {
            this.argument = argument;
            rawType = argument.getType();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        byte[] write(JsonMapper mapper, Object value) throws IOException {
            return mapper.writeValueAsBytes((Argument) argument, value);
        }

        T read(JsonMapper mapper, byte[] json) throws IOException {
            return mapper.readValue(json, argument);
        }
    }

    @SerdeableGenerated
    public record ConstructorShape(
        String name,
        int count,
        long id,
        boolean active,
        double score,
        String code,
        boolean enabled,
        double ratio,
        int size,
        String description) {
    }

    @SerdeableGenerated
    @Introspected
    public static final class GetterSetterShape {
        private String name;
        private int count;
        private long id;
        private boolean active;
        private double score;
        private String code;
        private boolean enabled;
        private double ratio;
        private int size;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getRatio() {
            return ratio;
        }

        public void setRatio(double ratio) {
            this.ratio = ratio;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class FieldShape {
        public String name;
        public int count;
        public long id;
        public boolean active;
        public double score;
        public String code;
        public boolean enabled;
        public double ratio;
        public int size;
        public String description;
    }
}
