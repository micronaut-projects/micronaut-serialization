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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.serializer.JsonbSerializer;

import java.io.IOException;

/**
 * Bridges {@link jakarta.json.bind.annotation.JsonbTypeSerializer} to Micronaut Serialization.
 */
@Internal
@Singleton
public final class JsonbTypeSerializerBridge implements Serializer<Object> {
    private static final Argument<JsonNode> JSON_NODE_ARGUMENT = Argument.of(JsonNode.class);

    private final JsonbBridgeSupport.ComponentFactory componentFactory;
    private final ObjectMapper mapper;

    /**
     * @param beanContext The Micronaut bean context used to resolve JSON-B serializer instances
     * @param mapper The cloned JSON-B mapper used for recursive serializer context operations
     */
    JsonbTypeSerializerBridge(BeanContext beanContext, ObjectMapper mapper) {
        this.componentFactory = new JsonbBridgeSupport.ComponentFactory(beanContext);
        this.mapper = mapper;
    }

    @Override
    public Serializer<Object> createSpecific(EncoderContext context, Argument<? extends Object> type) throws SerdeException {
        @SuppressWarnings("unchecked")
        JsonbSerializer<Object> serializer = (JsonbSerializer<Object>) componentFactory.get(
            JsonbBridgeSupport.serializerClass(type.getAnnotationMetadata())
        );
        Serializer<? super JsonNode> jsonNodeSerializer = context.findSerializer(JSON_NODE_ARGUMENT).createSpecific(context, JSON_NODE_ARGUMENT);
        JsonbFallbackCodec codec = new JsonbFallbackCodec(
            mapper,
            context.getSerdeConfiguration().orElse(null),
            BinaryDataStrategy.BYTE
        );
        return (encoder, _, _, value) -> {
            try {
                jsonNodeSerializer.serialize(encoder, context, JSON_NODE_ARGUMENT, JsonbJsonpBridge.writeWithJsonbSerializer(serializer, value, codec));
            } catch (JsonbException | IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new JsonbException("Cannot serialize JSON-B value with custom serializer", e);
            }
        };
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Object> type, Object value) throws IOException {
        createSpecific(context, type).serialize(encoder, context, type, value);
    }
}
