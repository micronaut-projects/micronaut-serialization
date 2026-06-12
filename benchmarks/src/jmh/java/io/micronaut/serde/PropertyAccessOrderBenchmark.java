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
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Measures deserialization sensitivity to JSON property order.
 */
public class PropertyAccessOrderBenchmark {

    private static final String JACKSON_DATABIND = "Jackson Databind";
    private static final String JACKSON_DATABIND_BLACKBIRD = "Jackson Databind Blackbird";
    private static final String SERDE_JACKSON_GENERATED = "Serde Jackson Generated";
    private static final String SERDE_JACKSON_RUNTIME = "Serde Jackson Runtime";

    private static final String[] PROPERTIES = {
        "\"name\":\"alpha\"",
        "\"count\":42",
        "\"id\":9000000123",
        "\"active\":true",
        "\"score\":123.456",
        "\"code\":\"C-0123456789\"",
        "\"enabled\":false",
        "\"ratio\":0.75",
        "\"size\":128",
        "\"description\":\"description-value\""
    };

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserialize(Holder holder) throws IOException {
        return holder.deserialize();
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
        PropertyAccessShapeBenchmark.Shape shape = PropertyAccessShapeBenchmark.Shape.CONSTRUCTOR;

        @Param({
            "ORDERED",
            "REVERSED",
            "ROTATED",
            "SHUFFLED"
        })
        PropertyOrder propertyOrder = PropertyOrder.ORDERED;

        private JsonMapper mapper;
        private ApplicationContext context;
        private PropertyAccessShapeBenchmark.ShapeCase<?> shapeCase;
        private byte[] json;

        @Setup
        public void setUp() throws Exception {
            shapeCase = shape.shapeCase();
            json = propertyOrder.json();
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
                    "micronaut.serde.serialization.inclusion", "ALWAYS"
                ));
                mapper = context.getBean(JacksonJsonMapper.class);
                validateMicronautSerde(true);
            } else if (stack.equals(SERDE_JACKSON_RUNTIME)) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS",
                    "micronaut.serde.serialization.disable-generated-serializer", true,
                    "micronaut.serde.deserialization.disable-generated-deserializer", true
                ));
                mapper = context.getBean(JacksonJsonMapper.class);
                validateMicronautSerde(false);
            } else {
                throw new IllegalStateException("Unsupported stack: " + stack);
            }
            mapper = mapper.createSpecific(shapeCase.argument);
            deserialize();
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }

        Object deserialize() throws IOException {
            return shapeCase.read(mapper, json);
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

    public enum PropertyOrder {
        ORDERED(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        REVERSED(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
        ROTATED(1, 2, 3, 4, 5, 6, 7, 8, 9, 0),
        SHUFFLED(4, 0, 8, 3, 9, 1, 7, 5, 2, 6);

        private final byte[] json;

        PropertyOrder(int... order) {
            StringBuilder builder = new StringBuilder(192);
            builder.append('{');
            for (int i = 0; i < order.length; i++) {
                if (i != 0) {
                    builder.append(',');
                }
                builder.append(PROPERTIES[order[i]]);
            }
            builder.append('}');
            json = builder.toString().getBytes(StandardCharsets.UTF_8);
        }

        byte[] json() {
            return json;
        }
    }
}
