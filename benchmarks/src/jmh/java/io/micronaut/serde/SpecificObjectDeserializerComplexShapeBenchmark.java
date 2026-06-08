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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import io.micronaut.serde.support.deserializers.ErrorCatchingDeserializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Measures complex bean shapes that intentionally route through {@code SpecificObjectDeserializer}.
 */
public class SpecificObjectDeserializerComplexShapeBenchmark {

    private static final String JACKSON_DATABIND = "Jackson Databind";
    private static final String JACKSON_DATABIND_BLACKBIRD = "Jackson Databind Blackbird";
    private static final String SERDE_JACKSON_RUNTIME = "Serde Jackson Runtime";
    private static final String SPECIFIC_OBJECT_DESERIALIZER = "io.micronaut.serde.support.deserializers.SpecificObjectDeserializer";
    private static final String SUBTYPED_PROPERTY_DESERIALIZER = "io.micronaut.serde.support.deserializers.SubtypedPropertyObjectDeserializer";
    private static final String SUBTYPED_DEDUCTION_DESERIALIZER = "io.micronaut.serde.support.deserializers.SubtypedDeductionDeserializer";
    private static final String WRAPPED_OBJECT_SUBTYPED_DESERIALIZER = "io.micronaut.serde.support.deserializers.WrappedObjectSubtypedDeserializer";

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
            SERDE_JACKSON_RUNTIME
        })
        String stack = SERDE_JACKSON_RUNTIME;

        @Param({
            "UNWRAPPED",
            "ANY_SETTER",
            "CREATOR_AND_INJECTED",
            "EXTERNAL_PROPERTY_SUBTYPE",
            "EXTERNAL_PROPERTY_SUBTYPE_TYPE_FIRST",
            "PROPERTY_SUBTYPE_LATE",
            "DEDUCTION_SUBTYPE",
            "WRAPPER_OBJECT_SUBTYPE",
            "VIEW_FILTERED",
            "MULTI_UNWRAPPED"
        })
        ComplexShape shape = ComplexShape.UNWRAPPED;

        private JsonMapper mapper;
        private ObjectReader objectReader;
        private ApplicationContext context;
        private ComplexCase<?> complexCase;

        @Setup
        public void setUp() throws Exception {
            complexCase = shape.complexCase();
            if (stack.equals(JACKSON_DATABIND)) {
                ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
                    .build();
                validateBlackbirdModule(objectMapper, false);
                objectReader = viewReader(objectMapper);
                mapper = new JacksonDatabindMapper(objectMapper);
            } else if (stack.equals(JACKSON_DATABIND_BLACKBIRD)) {
                ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
                    .addModule(new BlackbirdModule())
                    .build();
                validateBlackbirdModule(objectMapper, true);
                objectReader = viewReader(objectMapper);
                mapper = new JacksonDatabindMapper(objectMapper);
            } else if (stack.equals(SERDE_JACKSON_RUNTIME)) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS",
                    "micronaut.serde.serialization.disable-generated-serializer", true,
                    "micronaut.serde.deserialization.disable-generated-deserializer", true
                ));
                mapper = context.getBean(JacksonJsonMapper.class);
                validateMicronautRuntimeDeserializer();
            } else {
                throw new IllegalStateException("Unsupported stack: " + stack);
            }
            if (complexCase.view != null && objectReader == null) {
                mapper = mapper.cloneWithViewClass(complexCase.view);
            }
            if (objectReader == null) {
                mapper = mapper.createSpecific(complexCase.argument);
            }
            complexCase.validate(deserialize());
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }

        Object deserialize() throws IOException {
            if (objectReader != null) {
                return objectReader.readValue(complexCase.json);
            }
            return complexCase.read(mapper);
        }

        private ObjectReader viewReader(ObjectMapper objectMapper) {
            Class<?> view = complexCase.view;
            if (view == null) {
                return null;
            }
            return objectMapper.readerFor(complexCase.argument.getType()).withView(view);
        }

        private void validateMicronautRuntimeDeserializer() throws Exception {
            SerdeRegistry registry = context.getBean(SerdeRegistry.class);
            @SuppressWarnings({"unchecked", "rawtypes"})
            var deserializer = registry.findDeserializer((Argument) complexCase.argument)
                .createSpecific(registry.newDecoderContext(Object.class), (Argument) complexCase.argument);
            validateRuntime(
                "deserializer",
                complexCase.runtimeDeserializerClass,
                unwrapDeserializer(deserializer)
            );
        }

        private static Object unwrapDeserializer(Deserializer<?> deserializer) {
            if (deserializer instanceof ErrorCatchingDeserializer<?> errorCatchingDeserializer) {
                return errorCatchingDeserializer.getDeserializer();
            }
            return deserializer;
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
    }

    public enum ComplexShape {
        UNWRAPPED(new ComplexCase<>(
            Argument.of(UnwrappedShape.class),
            """
            {"name":"alpha","count":42,"id":9000000123,"active":true,"score":123.456,"code":"C-0123456789","description":"description-value"}
            """,
            value -> {
                UnwrappedShape unwrapped = (UnwrappedShape) value;
                if (unwrapped.nested == null || unwrapped.nested.id != 9000000123L) {
                    throw new IllegalStateException("Invalid unwrapped result");
                }
            }
        )),
        ANY_SETTER(new ComplexCase<>(
            Argument.of(AnySetterShape.class),
            """
            {"name":"alpha","count":42,"extra0":"v0","extra1":123,"extra2":true,"extra3":123.456}
            """,
            value -> {
                AnySetterShape anySetter = (AnySetterShape) value;
                if (anySetter.attributes.size() != 4 || !Boolean.TRUE.equals(anySetter.attributes.get("extra2"))) {
                    throw new IllegalStateException("Invalid any-setter result");
                }
            }
        )),
        CREATOR_AND_INJECTED(new ComplexCase<>(
            Argument.of(CreatorAndInjectedShape.class),
            """
            {"name":"alpha","count":42,"id":9000000123,"active":true,"score":123.456,"code":"C-0123456789"}
            """,
            value -> {
                CreatorAndInjectedShape creator = (CreatorAndInjectedShape) value;
                if (creator.getCount() != 42 || creator.getId() != 9000000123L || !creator.isActive()) {
                    throw new IllegalStateException("Invalid creator/injected result");
                }
            }
        )),
        EXTERNAL_PROPERTY_SUBTYPE(new ComplexCase<>(
            Argument.of(ExternalPropertySubtypeShape.class),
            """
            {"name":"alpha","event":{"code":"C-0123456789","id":9000000123,"score":123.456},"kind":"metric","count":42}
            """,
            value -> {
                ExternalPropertySubtypeShape external = (ExternalPropertySubtypeShape) value;
                if (!(external.event instanceof MetricEvent metricEvent) || metricEvent.id != 9000000123L) {
                    throw new IllegalStateException("Invalid external subtype result");
                }
            }
        )),
        EXTERNAL_PROPERTY_SUBTYPE_TYPE_FIRST(new ComplexCase<>(
            Argument.of(ExternalPropertySubtypeShape.class),
            """
            {"name":"alpha","kind":"metric","event":{"code":"C-0123456789","id":9000000123,"score":123.456},"count":42}
            """,
            value -> {
                ExternalPropertySubtypeShape external = (ExternalPropertySubtypeShape) value;
                if (!(external.event instanceof MetricEvent metricEvent) || metricEvent.id != 9000000123L) {
                    throw new IllegalStateException("Invalid external subtype type-first result");
                }
            }
        )),
        PROPERTY_SUBTYPE_LATE(new ComplexCase<>(
            Argument.of(PropertySubtypeEvent.class),
            """
            {"code":"C-0123456789","id":9000000123,"score":123.456,"kind":"metric"}
            """,
            value -> {
                if (!(value instanceof PropertyMetricEvent metricEvent) || metricEvent.id != 9000000123L) {
                    throw new IllegalStateException("Invalid property subtype late result");
                }
            },
            SUBTYPED_PROPERTY_DESERIALIZER
        )),
        DEDUCTION_SUBTYPE(new ComplexCase<>(
            Argument.of(DeductionEvent.class),
            """
            {"code":"C-0123456789","id":9000000123,"score":123.456}
            """,
            value -> {
                if (!(value instanceof DeductionMetricEvent metricEvent) || metricEvent.id != 9000000123L) {
                    throw new IllegalStateException("Invalid deduction subtype result");
                }
            },
            SUBTYPED_DEDUCTION_DESERIALIZER
        )),
        WRAPPER_OBJECT_SUBTYPE(new ComplexCase<>(
            Argument.of(WrapperEvent.class),
            """
            {"metric":{"code":"C-0123456789","id":9000000123,"score":123.456}}
            """,
            value -> {
                if (!(value instanceof WrapperMetricEvent metricEvent) || metricEvent.id != 9000000123L) {
                    throw new IllegalStateException("Invalid wrapper subtype result");
                }
            },
            WRAPPED_OBJECT_SUBTYPED_DESERIALIZER
        )),
        VIEW_FILTERED(new ComplexCase<>(
            Argument.of(ViewFilteredShape.class),
            """
            {"name":"alpha","count":42,"internalCode":"internal-value","score":123.456}
            """,
            value -> {
                ViewFilteredShape viewFiltered = (ViewFilteredShape) value;
                if (viewFiltered.count != 42 || viewFiltered.internalCode == null || viewFiltered.score != 123.456D) {
                    throw new IllegalStateException("Invalid view-filtered result");
                }
            },
            SPECIFIC_OBJECT_DESERIALIZER,
            Views.Internal.class
        )),
        MULTI_UNWRAPPED(new ComplexCase<>(
            Argument.of(MultiUnwrappedShape.class),
            """
            {"name":"alpha","left_id":9000000123,"left_active":true,"right_id":9000000456,"right_score":123.456,"description":"description-value"}
            """,
            value -> {
                MultiUnwrappedShape multiUnwrapped = (MultiUnwrappedShape) value;
                if (multiUnwrapped.left == null || multiUnwrapped.right == null || multiUnwrapped.right.id != 9000000456L) {
                    throw new IllegalStateException("Invalid multi-unwrapped result");
                }
            }
        ));

        private final ComplexCase<?> complexCase;

        ComplexShape(ComplexCase<?> complexCase) {
            this.complexCase = complexCase;
        }

        ComplexCase<?> complexCase() {
            return complexCase;
        }
    }

    static final class ComplexCase<T> {
        final Argument<T> argument;
        final byte[] json;
        final Validator validator;
        final String runtimeDeserializerClass;
        final Class<?> view;

        ComplexCase(Argument<T> argument, String json, Validator validator) {
            this(argument, json, validator, SPECIFIC_OBJECT_DESERIALIZER);
        }

        ComplexCase(Argument<T> argument, String json, Validator validator, String runtimeDeserializerClass) {
            this(argument, json, validator, runtimeDeserializerClass, null);
        }

        ComplexCase(Argument<T> argument, String json, Validator validator, String runtimeDeserializerClass, Class<?> view) {
            this.argument = argument;
            this.json = json.getBytes(StandardCharsets.UTF_8);
            this.validator = validator;
            this.runtimeDeserializerClass = runtimeDeserializerClass;
            this.view = view;
        }

        T read(JsonMapper mapper) throws IOException {
            return mapper.readValue(json, argument);
        }

        void validate(Object value) {
            validator.validate(value);
        }
    }

    @FunctionalInterface
    interface Validator {
        void validate(Object value);
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class UnwrappedShape {
        public String name;
        public int count;
        @JsonUnwrapped
        public NestedShape nested;
        public String description;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class NestedShape {
        public long id;
        public boolean active;
        public double score;
        public String code;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class AnySetterShape {
        public String name;
        public int count;
        public final Map<String, Object> attributes = new LinkedHashMap<>();

        @JsonAnySetter
        public void put(String key, Object value) {
            attributes.put(key, value);
        }
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected
    public static final class CreatorAndInjectedShape {
        private final String name;
        private final int count;
        private long id;
        private boolean active;
        private double score;
        private String code;

        @JsonCreator
        public CreatorAndInjectedShape(@JsonProperty("name") String name, @JsonProperty("count") int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
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
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class ExternalPropertySubtypeShape {
        public String name;
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "kind")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = MetricEvent.class, name = "metric"),
            @JsonSubTypes.Type(value = TextEvent.class, name = "text")
        })
        public Event event;
        public int count;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public abstract static class Event {
        public String code;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class MetricEvent extends Event {
        public long id;
        public double score;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class TextEvent extends Event {
        public String text;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PropertyMetricEvent.class, name = "metric"),
        @JsonSubTypes.Type(value = PropertyTextEvent.class, name = "text")
    })
    public abstract static class PropertySubtypeEvent {
        public String code;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class PropertyMetricEvent extends PropertySubtypeEvent {
        public long id;
        public double score;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class PropertyTextEvent extends PropertySubtypeEvent {
        public String text;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DeductionMetricEvent.class),
        @JsonSubTypes.Type(value = DeductionTextEvent.class)
    })
    public abstract static class DeductionEvent {
        public String code;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class DeductionMetricEvent extends DeductionEvent {
        public long id;
        public double score;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class DeductionTextEvent extends DeductionEvent {
        public String text;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = WrapperMetricEvent.class, name = "metric"),
        @JsonSubTypes.Type(value = WrapperTextEvent.class, name = "text")
    })
    public abstract static class WrapperEvent {
        public String code;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class WrapperMetricEvent extends WrapperEvent {
        public long id;
        public double score;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class WrapperTextEvent extends WrapperEvent {
        public String text;
    }

    static final class Views {
        static class Public {
        }

        static final class Internal extends Public {
        }
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class ViewFilteredShape {
        @JsonView(Views.Public.class)
        public String name;

        @JsonView(Views.Public.class)
        public int count;

        @JsonView(Views.Internal.class)
        public String internalCode;

        @JsonView(Views.Internal.class)
        public double score;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class MultiUnwrappedShape {
        public String name;

        @JsonUnwrapped(prefix = "left_")
        public LeftNestedShape left;

        @JsonUnwrapped(prefix = "right_")
        public RightNestedShape right;

        public String description;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class LeftNestedShape {
        public long id;
        public boolean active;
    }

    @Serdeable
    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class RightNestedShape {
        public long id;
        public double score;
    }
}
