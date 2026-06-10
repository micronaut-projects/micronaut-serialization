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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Bridges {@link jakarta.json.bind.annotation.JsonbTypeAdapter} to Micronaut Serialization.
 */
@Internal
@Singleton
@Bean(typed = JsonbTypeAdapterSerde.class)
public final class JsonbTypeAdapterSerde implements Serde<Object> {
    private final JsonbBridgeSupport.ComponentFactory componentFactory;

    /**
     * @param beanContext The Micronaut bean context used to resolve JSON-B adapter instances
     */
    JsonbTypeAdapterSerde(BeanContext beanContext) {
        this.componentFactory = new JsonbBridgeSupport.ComponentFactory(beanContext);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Object> type, Object value) throws IOException {
        createSpecific(context, type).serialize(encoder, context, type, value);
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return createSpecific(context, type).deserialize(decoder, context, type);
    }

    @Override
    public Serializer<Object> createSpecific(EncoderContext context, Argument<? extends Object> type) throws SerdeException {
        JsonbAdapter<Object, Object> adapter = adapter(type);
        return (encoder, c, _, value) -> {
            try {
                Object adapted = adapter.adaptToJson(value);
                @SuppressWarnings({"rawtypes", "unchecked"})
                Argument<Object> adaptedType = (Argument) Argument.of(adapted.getClass());
                Serializer<Object> serializer = c.findSerializer(adaptedType).createSpecific(c, adaptedType);
                serializer.serialize(encoder, c, adaptedType, adapted);
            } catch (Exception e) {
                throw new JsonbException("Cannot adapt JSON-B value for serialization", e);
            }
        };
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        @SuppressWarnings("rawtypes") Class<? extends JsonbAdapter> adapterClass = JsonbBridgeSupport.adapterClass(type.getAnnotationMetadata());
        JsonbAdapter<Object, Object> adapter = adapter(adapterClass);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Argument<Object> adaptedType = (Argument) Argument.of(JsonbBridgeSupport.adaptedType(adapterClass));
        @SuppressWarnings("unchecked")
        Deserializer<Object> deserializer = (Deserializer<Object>) context.findDeserializer(adaptedType).createSpecific(context, adaptedType);
        return new Deserializer<>() {
            @Override
            public @Nullable Object deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
                Object adapted = deserializer.deserializeNullable(decoder, context, adaptedType);
                try {
                    return adapter.adaptFromJson(adapted);
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B value for deserialization", e);
                }
            }

            @Override
            public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
                Object adapted = deserializer.deserialize(decoder, context, adaptedType);
                try {
                    return adapter.adaptFromJson(adapted);
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B value for deserialization", e);
                }
            }
        };
    }

    private JsonbAdapter<Object, Object> adapter(Argument<?> type) throws SerdeException {
        return adapter(JsonbBridgeSupport.adapterClass(type.getAnnotationMetadata()));
    }

    @SuppressWarnings("unchecked")
    private JsonbAdapter<Object, Object> adapter(Class<? extends JsonbAdapter> adapterClass) {
        return (JsonbAdapter<Object, Object>) componentFactory.get(adapterClass);
    }
}
