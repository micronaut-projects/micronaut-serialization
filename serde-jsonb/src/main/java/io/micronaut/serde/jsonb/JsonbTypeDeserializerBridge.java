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
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import java.io.IOException;

/**
 * Bridges {@link jakarta.json.bind.annotation.JsonbTypeDeserializer} to Micronaut Serialization.
 */
@Internal
@Singleton
@Bean(typed = JsonbTypeDeserializerBridge.class)
public final class JsonbTypeDeserializerBridge implements Deserializer<Object> {
    private final JsonbBridgeSupport.ComponentFactory componentFactory;
    private final ObjectMapper mapper;

    /**
     * @param beanContext The Micronaut bean context used to resolve JSON-B deserializer instances
     * @param mapper The cloned JSON-B mapper used for recursive deserializer context operations
     */
    JsonbTypeDeserializerBridge(BeanContext beanContext, ObjectMapper mapper) {
        this.componentFactory = new JsonbBridgeSupport.ComponentFactory(beanContext);
        this.mapper = mapper;
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        @SuppressWarnings("unchecked")
        JsonbDeserializer<Object> deserializer = (JsonbDeserializer<Object>) componentFactory.get(
            JsonbBridgeSupport.deserializerClass(type.getAnnotationMetadata())
        );
        JsonbFallbackCodec codec = new JsonbFallbackCodec(
            mapper,
            context.getSerdeConfiguration().orElse(null)
        );
        return (decoder, context1, type1) -> {
            try (JsonParser parser = JsonbJsonpBridge.parserForDeserializer(decoder)) {
                return deserializer.deserialize(parser, new JsonbDeserializationContext(codec), type1.getType());
            } catch (JsonbException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new JsonbException("Cannot deserialize JSON-B value with custom deserializer", e);
            }
        };
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return createSpecific(context, type).deserialize(decoder, context, type);
    }
}
