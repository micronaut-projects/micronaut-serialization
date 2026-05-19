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
 * Compares primitive, boxed, and object property value paths while keeping property count and key order constant.
 */
public class PropertyValueKindBenchmark {

    private static final String JACKSON_DATABIND = "Jackson Databind";
    private static final String JACKSON_DATABIND_BLACKBIRD = "Jackson Databind Blackbird";
    private static final String SERDE_JACKSON_GENERATED = "Serde Jackson Generated";
    private static final String SERDE_JACKSON_RUNTIME = "Serde Jackson Runtime";
    private static final byte[] PRIMITIVE_JSON = """
        {"a":1000,"b":9000000123,"c":true,"d":123.456,"e":2000,"f":9000000456,"g":false,"h":789.123,"i":3000,"j":9000000789}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] INT_JSON = """
        {"a":1000,"b":2000,"c":3000,"d":4000,"e":5000,"f":6000,"g":7000,"h":8000,"i":9000,"j":10000}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] LONG_JSON = """
        {"a":9000000001,"b":9000000002,"c":9000000003,"d":9000000004,"e":9000000005,"f":9000000006,"g":9000000007,"h":9000000008,"i":9000000009,"j":9000000010}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] BOOLEAN_JSON = """
        {"a":true,"b":false,"c":true,"d":false,"e":true,"f":false,"g":true,"h":false,"i":true,"j":false}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] DOUBLE_JSON = """
        {"a":1000.001,"b":2000.002,"c":3000.003,"d":4000.004,"e":5000.005,"f":6000.006,"g":7000.007,"h":8000.008,"i":9000.009,"j":10000.01}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] STRING_JSON = """
        {"a":"value-1000","b":"value-9000000123","c":"value-true","d":"value-123.456","e":"value-2000","f":"value-9000000456","g":"value-false","h":"value-789.123","i":"value-3000","j":"value-9000000789"}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] OBJECT_JSON = STRING_JSON;

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

        @Param({
            "PRIMITIVE",
            "ALL_INT",
            "ALL_LONG",
            "ALL_BOOLEAN",
            "ALL_DOUBLE",
            "ALL_STRING",
            "BOXED",
            "OBJECT"
        })
        ValueKind valueKind = ValueKind.PRIMITIVE;

        @Param({"true"})
        boolean failOnNullForPrimitives = true;

        private JsonMapper mapper;
        private ApplicationContext context;
        private ShapeCase<?> shapeCase;
        private Object value;

        @Setup
        public void setUp() throws Exception {
            shapeCase = valueKind.shapeCase(shape);
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
            value = deserialize();
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
            return shapeCase.read(mapper);
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
        CONSTRUCTOR,
        GETTER_SETTER,
        FIELD
    }

    public enum ValueKind {
        PRIMITIVE {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(PrimitiveConstructorShape.class), PRIMITIVE_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(PrimitiveGetterSetterShape.class), PRIMITIVE_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(PrimitiveFieldShape.class), PRIMITIVE_JSON);
                };
            }
        },
        ALL_INT {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(IntConstructorShape.class), INT_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(IntGetterSetterShape.class), INT_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(IntFieldShape.class), INT_JSON);
                };
            }
        },
        ALL_LONG {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(LongConstructorShape.class), LONG_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(LongGetterSetterShape.class), LONG_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(LongFieldShape.class), LONG_JSON);
                };
            }
        },
        ALL_BOOLEAN {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(BooleanConstructorShape.class), BOOLEAN_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(BooleanGetterSetterShape.class), BOOLEAN_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(BooleanFieldShape.class), BOOLEAN_JSON);
                };
            }
        },
        ALL_DOUBLE {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(DoubleConstructorShape.class), DOUBLE_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(DoubleGetterSetterShape.class), DOUBLE_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(DoubleFieldShape.class), DOUBLE_JSON);
                };
            }
        },
        ALL_STRING {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(StringConstructorShape.class), STRING_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(StringGetterSetterShape.class), STRING_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(StringFieldShape.class), STRING_JSON);
                };
            }
        },
        BOXED {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(BoxedConstructorShape.class), PRIMITIVE_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(BoxedGetterSetterShape.class), PRIMITIVE_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(BoxedFieldShape.class), PRIMITIVE_JSON);
                };
            }
        },
        OBJECT {
            @Override
            ShapeCase<?> shapeCase(Shape shape) {
                return switch (shape) {
                    case CONSTRUCTOR -> new ShapeCase<>(Argument.of(ObjectConstructorShape.class), OBJECT_JSON);
                    case GETTER_SETTER -> new ShapeCase<>(Argument.of(ObjectGetterSetterShape.class), OBJECT_JSON);
                    case FIELD -> new ShapeCase<>(Argument.of(ObjectFieldShape.class), OBJECT_JSON);
                };
            }
        };

        abstract ShapeCase<?> shapeCase(Shape shape);
    }

    static final class ShapeCase<T> {
        final Argument<T> argument;
        final Class<T> rawType;
        final byte[] json;

        ShapeCase(Argument<T> argument, byte[] json) {
            this.argument = argument;
            this.rawType = argument.getType();
            this.json = json;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        byte[] write(JsonMapper mapper, Object value) throws IOException {
            return mapper.writeValueAsBytes((Argument) argument, value);
        }

        T read(JsonMapper mapper) throws IOException {
            return mapper.readValue(json, argument);
        }
    }

    @SerdeableGenerated
    public record PrimitiveConstructorShape(
        int a,
        long b,
        boolean c,
        double d,
        int e,
        long f,
        boolean g,
        double h,
        int i,
        long j) {
    }

    @SerdeableGenerated
    public record IntConstructorShape(
        int a,
        int b,
        int c,
        int d,
        int e,
        int f,
        int g,
        int h,
        int i,
        int j) {
    }

    @SerdeableGenerated
    public record LongConstructorShape(
        long a,
        long b,
        long c,
        long d,
        long e,
        long f,
        long g,
        long h,
        long i,
        long j) {
    }

    @SerdeableGenerated
    public record BooleanConstructorShape(
        boolean a,
        boolean b,
        boolean c,
        boolean d,
        boolean e,
        boolean f,
        boolean g,
        boolean h,
        boolean i,
        boolean j) {
    }

    @SerdeableGenerated
    public record DoubleConstructorShape(
        double a,
        double b,
        double c,
        double d,
        double e,
        double f,
        double g,
        double h,
        double i,
        double j) {
    }

    @SerdeableGenerated
    public record StringConstructorShape(
        String a,
        String b,
        String c,
        String d,
        String e,
        String f,
        String g,
        String h,
        String i,
        String j) {
    }

    @SerdeableGenerated
    public record BoxedConstructorShape(
        Integer a,
        Long b,
        Boolean c,
        Double d,
        Integer e,
        Long f,
        Boolean g,
        Double h,
        Integer i,
        Long j) {
    }

    @SerdeableGenerated
    public record ObjectConstructorShape(
        String a,
        String b,
        String c,
        String d,
        String e,
        String f,
        String g,
        String h,
        String i,
        String j) {
    }

    @SerdeableGenerated
    @Introspected
    public static final class PrimitiveGetterSetterShape {
        private int a;
        private long b;
        private boolean c;
        private double d;
        private int e;
        private long f;
        private boolean g;
        private double h;
        private int i;
        private long j;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public long getB() {
            return b;
        }

        public void setB(long b) {
            this.b = b;
        }

        public boolean isC() {
            return c;
        }

        public void setC(boolean c) {
            this.c = c;
        }

        public double getD() {
            return d;
        }

        public void setD(double d) {
            this.d = d;
        }

        public int getE() {
            return e;
        }

        public void setE(int e) {
            this.e = e;
        }

        public long getF() {
            return f;
        }

        public void setF(long f) {
            this.f = f;
        }

        public boolean isG() {
            return g;
        }

        public void setG(boolean g) {
            this.g = g;
        }

        public double getH() {
            return h;
        }

        public void setH(double h) {
            this.h = h;
        }

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        public long getJ() {
            return j;
        }

        public void setJ(long j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class IntGetterSetterShape {
        private int a;
        private int b;
        private int c;
        private int d;
        private int e;
        private int f;
        private int g;
        private int h;
        private int i;
        private int j;

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public int getB() {
            return b;
        }

        public void setB(int b) {
            this.b = b;
        }

        public int getC() {
            return c;
        }

        public void setC(int c) {
            this.c = c;
        }

        public int getD() {
            return d;
        }

        public void setD(int d) {
            this.d = d;
        }

        public int getE() {
            return e;
        }

        public void setE(int e) {
            this.e = e;
        }

        public int getF() {
            return f;
        }

        public void setF(int f) {
            this.f = f;
        }

        public int getG() {
            return g;
        }

        public void setG(int g) {
            this.g = g;
        }

        public int getH() {
            return h;
        }

        public void setH(int h) {
            this.h = h;
        }

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }

        public int getJ() {
            return j;
        }

        public void setJ(int j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class LongGetterSetterShape {
        private long a;
        private long b;
        private long c;
        private long d;
        private long e;
        private long f;
        private long g;
        private long h;
        private long i;
        private long j;

        public long getA() {
            return a;
        }

        public void setA(long a) {
            this.a = a;
        }

        public long getB() {
            return b;
        }

        public void setB(long b) {
            this.b = b;
        }

        public long getC() {
            return c;
        }

        public void setC(long c) {
            this.c = c;
        }

        public long getD() {
            return d;
        }

        public void setD(long d) {
            this.d = d;
        }

        public long getE() {
            return e;
        }

        public void setE(long e) {
            this.e = e;
        }

        public long getF() {
            return f;
        }

        public void setF(long f) {
            this.f = f;
        }

        public long getG() {
            return g;
        }

        public void setG(long g) {
            this.g = g;
        }

        public long getH() {
            return h;
        }

        public void setH(long h) {
            this.h = h;
        }

        public long getI() {
            return i;
        }

        public void setI(long i) {
            this.i = i;
        }

        public long getJ() {
            return j;
        }

        public void setJ(long j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class BooleanGetterSetterShape {
        private boolean a;
        private boolean b;
        private boolean c;
        private boolean d;
        private boolean e;
        private boolean f;
        private boolean g;
        private boolean h;
        private boolean i;
        private boolean j;

        public boolean isA() {
            return a;
        }

        public void setA(boolean a) {
            this.a = a;
        }

        public boolean isB() {
            return b;
        }

        public void setB(boolean b) {
            this.b = b;
        }

        public boolean isC() {
            return c;
        }

        public void setC(boolean c) {
            this.c = c;
        }

        public boolean isD() {
            return d;
        }

        public void setD(boolean d) {
            this.d = d;
        }

        public boolean isE() {
            return e;
        }

        public void setE(boolean e) {
            this.e = e;
        }

        public boolean isF() {
            return f;
        }

        public void setF(boolean f) {
            this.f = f;
        }

        public boolean isG() {
            return g;
        }

        public void setG(boolean g) {
            this.g = g;
        }

        public boolean isH() {
            return h;
        }

        public void setH(boolean h) {
            this.h = h;
        }

        public boolean isI() {
            return i;
        }

        public void setI(boolean i) {
            this.i = i;
        }

        public boolean isJ() {
            return j;
        }

        public void setJ(boolean j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class DoubleGetterSetterShape {
        private double a;
        private double b;
        private double c;
        private double d;
        private double e;
        private double f;
        private double g;
        private double h;
        private double i;
        private double j;

        public double getA() {
            return a;
        }

        public void setA(double a) {
            this.a = a;
        }

        public double getB() {
            return b;
        }

        public void setB(double b) {
            this.b = b;
        }

        public double getC() {
            return c;
        }

        public void setC(double c) {
            this.c = c;
        }

        public double getD() {
            return d;
        }

        public void setD(double d) {
            this.d = d;
        }

        public double getE() {
            return e;
        }

        public void setE(double e) {
            this.e = e;
        }

        public double getF() {
            return f;
        }

        public void setF(double f) {
            this.f = f;
        }

        public double getG() {
            return g;
        }

        public void setG(double g) {
            this.g = g;
        }

        public double getH() {
            return h;
        }

        public void setH(double h) {
            this.h = h;
        }

        public double getI() {
            return i;
        }

        public void setI(double i) {
            this.i = i;
        }

        public double getJ() {
            return j;
        }

        public void setJ(double j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class StringGetterSetterShape {
        private String a;
        private String b;
        private String c;
        private String d;
        private String e;
        private String f;
        private String g;
        private String h;
        private String i;
        private String j;

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public String getC() {
            return c;
        }

        public void setC(String c) {
            this.c = c;
        }

        public String getD() {
            return d;
        }

        public void setD(String d) {
            this.d = d;
        }

        public String getE() {
            return e;
        }

        public void setE(String e) {
            this.e = e;
        }

        public String getF() {
            return f;
        }

        public void setF(String f) {
            this.f = f;
        }

        public String getG() {
            return g;
        }

        public void setG(String g) {
            this.g = g;
        }

        public String getH() {
            return h;
        }

        public void setH(String h) {
            this.h = h;
        }

        public String getI() {
            return i;
        }

        public void setI(String i) {
            this.i = i;
        }

        public String getJ() {
            return j;
        }

        public void setJ(String j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class BoxedGetterSetterShape {
        private Integer a;
        private Long b;
        private Boolean c;
        private Double d;
        private Integer e;
        private Long f;
        private Boolean g;
        private Double h;
        private Integer i;
        private Long j;

        public Integer getA() {
            return a;
        }

        public void setA(Integer a) {
            this.a = a;
        }

        public Long getB() {
            return b;
        }

        public void setB(Long b) {
            this.b = b;
        }

        public Boolean getC() {
            return c;
        }

        public void setC(Boolean c) {
            this.c = c;
        }

        public Double getD() {
            return d;
        }

        public void setD(Double d) {
            this.d = d;
        }

        public Integer getE() {
            return e;
        }

        public void setE(Integer e) {
            this.e = e;
        }

        public Long getF() {
            return f;
        }

        public void setF(Long f) {
            this.f = f;
        }

        public Boolean getG() {
            return g;
        }

        public void setG(Boolean g) {
            this.g = g;
        }

        public Double getH() {
            return h;
        }

        public void setH(Double h) {
            this.h = h;
        }

        public Integer getI() {
            return i;
        }

        public void setI(Integer i) {
            this.i = i;
        }

        public Long getJ() {
            return j;
        }

        public void setJ(Long j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected
    public static final class ObjectGetterSetterShape {
        private String a;
        private String b;
        private String c;
        private String d;
        private String e;
        private String f;
        private String g;
        private String h;
        private String i;
        private String j;

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public String getC() {
            return c;
        }

        public void setC(String c) {
            this.c = c;
        }

        public String getD() {
            return d;
        }

        public void setD(String d) {
            this.d = d;
        }

        public String getE() {
            return e;
        }

        public void setE(String e) {
            this.e = e;
        }

        public String getF() {
            return f;
        }

        public void setF(String f) {
            this.f = f;
        }

        public String getG() {
            return g;
        }

        public void setG(String g) {
            this.g = g;
        }

        public String getH() {
            return h;
        }

        public void setH(String h) {
            this.h = h;
        }

        public String getI() {
            return i;
        }

        public void setI(String i) {
            this.i = i;
        }

        public String getJ() {
            return j;
        }

        public void setJ(String j) {
            this.j = j;
        }
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class PrimitiveFieldShape {
        public int a;
        public long b;
        public boolean c;
        public double d;
        public int e;
        public long f;
        public boolean g;
        public double h;
        public int i;
        public long j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class IntFieldShape {
        public int a;
        public int b;
        public int c;
        public int d;
        public int e;
        public int f;
        public int g;
        public int h;
        public int i;
        public int j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class LongFieldShape {
        public long a;
        public long b;
        public long c;
        public long d;
        public long e;
        public long f;
        public long g;
        public long h;
        public long i;
        public long j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class BooleanFieldShape {
        public boolean a;
        public boolean b;
        public boolean c;
        public boolean d;
        public boolean e;
        public boolean f;
        public boolean g;
        public boolean h;
        public boolean i;
        public boolean j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class DoubleFieldShape {
        public double a;
        public double b;
        public double c;
        public double d;
        public double e;
        public double f;
        public double g;
        public double h;
        public double i;
        public double j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class StringFieldShape {
        public String a;
        public String b;
        public String c;
        public String d;
        public String e;
        public String f;
        public String g;
        public String h;
        public String i;
        public String j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class BoxedFieldShape {
        public Integer a;
        public Long b;
        public Boolean c;
        public Double d;
        public Integer e;
        public Long f;
        public Boolean g;
        public Double h;
        public Integer i;
        public Long j;
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class ObjectFieldShape {
        public String a;
        public String b;
        public String c;
        public String d;
        public String e;
        public String f;
        public String g;
        public String h;
        public String i;
        public String j;
    }
}
