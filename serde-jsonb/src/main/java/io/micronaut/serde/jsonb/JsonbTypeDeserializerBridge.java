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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Objects;

/**
 * Bridges {@link jakarta.json.bind.annotation.JsonbTypeDeserializer} to Micronaut Serialization.
 */
@Internal
@Singleton
public final class JsonbTypeDeserializerBridge implements Deserializer<Object> {
    private final JsonbBridgeSupport.ComponentFactory componentFactory;
    private final JsonMapper mapper;

    JsonbTypeDeserializerBridge(BeanContext beanContext, JsonMapper mapper) {
        this.componentFactory = new JsonbBridgeSupport.ComponentFactory(beanContext);
        this.mapper = mapper;
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        @SuppressWarnings("unchecked")
        JsonbDeserializer<Object> deserializer = (JsonbDeserializer<Object>) componentFactory.get(
            JsonbBridgeSupport.deserializerClass(type.getAnnotationMetadata())
        );
        return new Deserializer<>() {
            @Override
            public @Nullable Object deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
                if (decoder.decodeNull()) {
                    return null;
                }
                return deserialize(decoder, context, type);
            }

            @Override
            public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
                Object value = mapper.readValueFromTree(decoder.decodeNode(), Argument.OBJECT_ARGUMENT);
                try (JsonParser parser = JsonbBridgeSupport.parserFor(mapper, value)) {
                    if (value instanceof Collection<?>) {
                        parser.next();
                    }
                    return deserializer.deserialize(parser, new JsonbDeserializationContext(mapper), type.getType());
                } catch (JsonbException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw new JsonbException("Cannot deserialize JSON-B value with custom deserializer", e);
                }
            }
        };
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return createSpecific(context, type).deserialize(decoder, context, type);
    }

    private record JsonbDeserializationContext(
        JsonMapper mapper) implements DeserializationContext {

        @Override
            public <T> T deserialize(Class<T> type, JsonParser parser) {
                return deserialize((Type) type, parser);
            }

            @Override
            @SuppressWarnings("TypeParameterUnusedInFormals")
            public <T> T deserialize(Type type, JsonParser parser) {
                try {
                    Object value = JsonbBridgeSupport.parseNext(parser);
                    @SuppressWarnings("unchecked")
                    T result = (T) mapper.readValue(JsonbBridgeSupport.writeJson(mapper, value), Argument.of(type));
                    return Objects.requireNonNull(result, "JSON-B deserialization context result");
                } catch (IOException e) {
                    throw new JsonbException("Cannot deserialize JSON-B context value", e);
                }
            }
        }
}
