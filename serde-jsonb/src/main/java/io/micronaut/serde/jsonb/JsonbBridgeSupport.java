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
package io.micronaut.serde.jsonb;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.spi.JsonProvider;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class JsonbBridgeSupport {
    private JsonbBridgeSupport() {
    }

    static final class ComponentFactory {
        private final BeanContext beanContext;

        ComponentFactory(BeanContext beanContext) {
            this.beanContext = beanContext;
        }

        <T> T get(Class<T> type) {
            return beanContext.findBean(type)
                .or(() -> cdiBean(type))
                .orElseGet(() -> instantiate(type));
        }

        static <T> T instantiate(Class<T> type) {
            try {
                Constructor<T> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new JsonbException("Cannot instantiate JSON-B component " + type.getName(), e);
            }
        }

        static <T> java.util.Optional<T> cdiBean(Class<T> type) {
            try {
                Class<?> cdiType = Class.forName("jakarta.enterprise.inject.spi.CDI");
                Object cdi = cdiType.getMethod("current").invoke(null);
                Method select = cdi.getClass().getMethod("select", Class.class, Annotation[].class);
                Object instance = select.invoke(cdi, type, new Annotation[0]);
                return java.util.Optional.of(type.cast(instance.getClass().getMethod("get").invoke(instance)));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return java.util.Optional.empty();
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbAdapter> adapterClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "adapter")
            .map(type -> type.asSubclass(JsonbAdapter.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B adapter metadata"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbSerializer> serializerClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "serializer")
            .map(type -> type.asSubclass(JsonbSerializer.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B serializer metadata"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Class<? extends JsonbDeserializer> deserializerClass(AnnotationMetadata annotationMetadata) throws SerdeException {
        return annotationMetadata.classValue(JsonbSerdeConfig.class, "deserializer")
            .map(type -> type.asSubclass(JsonbDeserializer.class))
            .orElseThrow(() -> new SerdeException("Missing JSON-B deserializer metadata"));
    }

    @SuppressWarnings({"rawtypes"})
    static Type adaptedType(Class<? extends JsonbAdapter> adapterClass) {
        Type type = findAdapterType(adapterClass);
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 2) {
            return parameterizedType.getActualTypeArguments()[1];
        }
        return Object.class;
    }

    private static @Nullable Type findAdapterType(Class<?> type) {
        for (Type genericInterface : type.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() == JsonbAdapter.class) {
                return parameterizedType;
            }
            if (genericInterface instanceof Class<?> interfaceClass) {
                Type adapterType = findAdapterType(interfaceClass);
                if (adapterType != null) {
                    return adapterType;
                }
            }
        }
        Type genericSuperclass = type.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType parameterizedType
            && parameterizedType.getRawType() instanceof Class<?> superClass) {
            if (superClass == JsonbAdapter.class) {
                return parameterizedType;
            }
            return findAdapterType(superClass);
        }
        if (genericSuperclass instanceof Class<?> superClass && superClass != Object.class) {
            return findAdapterType(superClass);
        }
        return null;
    }

    static void encodeAny(Encoder encoder, @Nullable Object value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else if (value instanceof String string) {
            encoder.encodeString(string);
        } else if (value instanceof Boolean bool) {
            encoder.encodeBoolean(bool);
        } else if (value instanceof BigInteger integer) {
            encoder.encodeBigInteger(integer);
        } else if (value instanceof BigDecimal decimal) {
            encoder.encodeBigDecimal(decimal);
        } else if (value instanceof Number number) {
            if (value instanceof Float || value instanceof Double) {
                encoder.encodeDouble(number.doubleValue());
            } else {
                encoder.encodeLong(number.longValue());
            }
        } else if (value instanceof Map<?, ?> map) {
            Encoder objectEncoder = encoder.encodeObject(Argument.mapOf(String.class, Object.class));
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                objectEncoder.encodeKey(String.valueOf(entry.getKey()));
                encodeAny(objectEncoder, entry.getValue());
            }
            objectEncoder.finishStructure();
        } else if (value instanceof Iterable<?> iterable) {
            Encoder arrayEncoder = encoder.encodeArray(Argument.listOf(Object.class));
            for (Object item : iterable) {
                encodeAny(arrayEncoder, item);
            }
            arrayEncoder.finishStructure();
        } else if (value.getClass().isArray()) {
            Encoder arrayEncoder = encoder.encodeArray(Argument.listOf(Object.class));
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                encodeAny(arrayEncoder, java.lang.reflect.Array.get(value, i));
            }
            arrayEncoder.finishStructure();
        } else {
            encoder.encodeString(String.valueOf(value));
        }
    }

    static @Nullable Object readJson(String json, JsonMapper mapper) throws IOException {
        return mapper.readValue(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Argument.OBJECT_ARGUMENT);
    }

    static String writeJson(JsonMapper mapper, @Nullable Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }

    static final class JsonbSerializationContext implements SerializationContext {
        private final JsonMapper mapper;

        JsonbSerializationContext(JsonMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public <T> void serialize(String key, T object, JsonGenerator generator) {
            generator.writeKey(key);
            serialize(object, generator);
        }

        @Override
        public <T> void serialize(T object, JsonGenerator generator) {
            writeJsonValue(generator, object);
        }

        private void writeJsonValue(JsonGenerator generator, @Nullable Object object) {
            try {
                Object jsonValue = readJson(writeJson(mapper, object), mapper);
                writeParsedJsonValue(generator, jsonValue);
            } catch (IOException e) {
                throw new JsonbException("Cannot serialize JSON-B context value", e);
            }
        }
    }

    static void writeParsedJsonValue(JsonGenerator generator, @Nullable Object value) {
        switch (value) {
            case null -> generator.writeNull();
            case String string -> generator.write(string);
            case Boolean bool -> generator.write(bool);
            case BigInteger integer -> generator.write(integer);
            case BigDecimal decimal -> generator.write(decimal);
            case Number number -> generator.write(number.doubleValue());
            case Map<?, ?> map -> {
                generator.writeStartObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    writeParsedJsonValue(String.valueOf(entry.getKey()), generator, entry.getValue());
                }
                generator.writeEnd();
            }
            case Iterable<?> iterable -> {
                generator.writeStartArray();
                for (Object item : iterable) {
                    writeParsedJsonValue(generator, item);
                }
                generator.writeEnd();
            }
            default -> generator.write(String.valueOf(value));
        }
    }

    private static void writeParsedJsonValue(String key, JsonGenerator generator, @Nullable Object value) {
        if (value == null) {
            generator.writeNull(key);
        } else {
            generator.writeKey(key);
            writeParsedJsonValue(generator, value);
        }
    }

    static JsonParser parserFor(JsonMapper mapper, @Nullable Object value) throws IOException {
        return JsonProvider.provider().createParser(new StringReader(writeJson(mapper, value)));
    }

    static @Nullable Object parseNext(JsonParser parser) {
        return parse(parser, parser.next());
    }

    private static @Nullable Object parse(JsonParser parser, JsonParser.Event event) {
        return switch (event) {
            case START_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (parser.hasNext()) {
                    JsonParser.Event next = parser.next();
                    if (next == JsonParser.Event.END_OBJECT) {
                        break;
                    }
                    if (next == JsonParser.Event.KEY_NAME) {
                        String key = parser.getString();
                        map.put(key, Objects.requireNonNull(parseNext(parser)));
                    }
                }
                yield map;
            }
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (parser.hasNext()) {
                    JsonParser.Event next = parser.next();
                    if (next == JsonParser.Event.END_ARRAY) {
                        break;
                    }
                    list.add(Objects.requireNonNull(parse(parser, next)));
                }
                yield list;
            }
            case KEY_NAME -> {
                Map<String, Object> map = new LinkedHashMap<>();
                JsonParser.Event current = event;
                while (current == JsonParser.Event.KEY_NAME) {
                    String key = parser.getString();
                    map.put(key, Objects.requireNonNull(parseNext(parser)));
                    if (!parser.hasNext()) {
                        break;
                    }
                    current = parser.next();
                    if (current == JsonParser.Event.END_OBJECT) {
                        break;
                    }
                }
                yield map;
            }
            case VALUE_STRING -> parser.getString();
            case VALUE_NUMBER -> parser.getBigDecimal();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> null;
            case END_ARRAY, END_OBJECT -> throw new JsonbException("Unexpected JSON parser event: " + event);
        };
    }

    static @Nullable Object writeWithJsonbSerializer(JsonbSerializer<Object> serializer, Object value, JsonMapper mapper) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JsonProvider.provider().createGenerator(writer)) {
            serializer.serialize(value, generator, new JsonbSerializationContext(mapper));
        }
        return readJson(writer.toString(), mapper);
    }
}
