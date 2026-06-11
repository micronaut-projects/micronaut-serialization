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

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.stream.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Bridges JSON-B config-level adapters, serializers, and deserializers into
 * Serde's property and element codec lookup.
 * <p>
 * Runtime introspections attach this serde through synthetic metadata when a
 * configured JSON-B customization applies to a property or collection element.
 */
@Internal
@Bean(typed = JsonbConfiguredSerde.class)
@SuppressWarnings("java:S3776")
public final class JsonbConfiguredSerde implements Serde<Object> {
    private static final Argument<JsonNode> JSON_NODE_ARGUMENT = Argument.of(JsonNode.class);

    private final ObjectMapper mapper;

    /**
     * @param mapper The cloned JSON-B mapper used for recursive configured
     *               customization codec operations
     */
    JsonbConfiguredSerde(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Serializer<Object> createSpecific(EncoderContext context, Argument<? extends Object> type) throws SerdeException {
        JsonbRuntimeCustomizations customizations = customizations(type);
        Serializer<? super JsonNode> jsonNodeSerializer = context.findSerializer(JSON_NODE_ARGUMENT).createSpecific(context, JSON_NODE_ARGUMENT);
        JsonbFallbackCodec codec = new JsonbFallbackCodec(
            mapper,
            context.getSerdeConfiguration().orElse(null),
            BinaryDataStrategy.BYTE
        );
        return (encoder, c, _, value) -> {
            try {
                JsonbRuntimeCustomizations.ConfigSerializer configuredSerializer = customizations.serializer(value.getClass());
                if (configuredSerializer != null) {
                    jsonNodeSerializer.serialize(encoder, c, JSON_NODE_ARGUMENT, JsonbJsonpBridge.writeWithJsonbSerializer(configuredSerializer.serializer(), value, codec));
                    return;
                }
                JsonbRuntimeCustomizations.ConfigAdapter adapter = customizations.adapter(value.getClass());
                if (adapter != null) {
                    Object adapted = adapter.adapter().adaptToJson(value);
                    if (adapted == null) {
                        encoder.encodeNull();
                        return;
                    }
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Argument<Object> adaptedType = (Argument) Argument.of(adapted.getClass());
                    Serializer<Object> serializer = c.findSerializer(adaptedType).createSpecific(c, adaptedType);
                    serializer.serialize(encoder, c, adaptedType, adapted);
                    return;
                }
                if (value instanceof Iterable<?> iterable && type.getTypeParameters().length > 0) {
                    serializeIterable(encoder, c, type, iterable, customizations, jsonNodeSerializer, codec);
                    return;
                }
                throw new JsonbException("No configured JSON-B serializer for type " + value.getClass().getName());
            } catch (JsonbException | IOException e) {
                throw e;
            } catch (Exception e) {
                throw new JsonbException("Cannot serialize JSON-B value with configured customization", e);
            }
        };
    }

    private static void serializeIterable(Encoder encoder,
                                          EncoderContext context,
                                          Argument<?> type,
                                          Iterable<?> iterable,
                                          JsonbRuntimeCustomizations customizations,
                                          Serializer<? super JsonNode> jsonNodeSerializer,
                                          JsonbFallbackCodec codec) throws Exception {
        Argument<?> elementType = type.getTypeParameters()[0];
        Encoder array = encoder.encodeArray(type);
        Class<?> lastItemClass = null;
        JsonbRuntimeCustomizations.@Nullable ConfigSerializer lastConfiguredSerializer = null;
        JsonbRuntimeCustomizations.@Nullable ConfigAdapter lastAdapter = null;
        @Nullable Serializer<Object> lastSerializer = null;
        for (Object item : iterable) {
            if (item == null) {
                array.encodeNull();
                continue;
            }
            Class<?> itemClass = item.getClass();
            if (itemClass != lastItemClass) {
                lastItemClass = itemClass;
                lastConfiguredSerializer = customizations.serializer(itemClass);
                lastAdapter = customizations.adapter(itemClass);
                lastSerializer = null;
            }
            JsonbRuntimeCustomizations.ConfigSerializer configuredSerializer = lastConfiguredSerializer;
            if (configuredSerializer != null) {
                jsonNodeSerializer.serialize(array, context, JSON_NODE_ARGUMENT, JsonbJsonpBridge.writeWithJsonbSerializer(configuredSerializer.serializer(), item, codec));
                continue;
            }
            JsonbRuntimeCustomizations.ConfigAdapter adapter = lastAdapter;
            if (adapter != null) {
                Object adapted = adapter.adapter().adaptToJson(item);
                if (adapted == null) {
                    array.encodeNull();
                    continue;
                }
                @SuppressWarnings({"rawtypes", "unchecked"})
                Argument<Object> adaptedType = (Argument) Argument.of(adapted.getClass());
                Serializer<Object> serializer = context.findSerializer(adaptedType).createSpecific(context, adaptedType);
                serializer.serialize(array, context, adaptedType, adapted);
                continue;
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            Argument<Object> runtimeType = (Argument) Argument.of(itemClass, elementType.getAnnotationMetadata());
            Serializer<Object> serializer = lastSerializer;
            if (serializer == null) {
                serializer = context.findSerializer(runtimeType).createSpecific(context, runtimeType);
                lastSerializer = serializer;
            }
            serializer.serialize(array, context, runtimeType, item);
        }
        array.finishStructure();
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        JsonbRuntimeCustomizations customizations = customizations(type);
        JsonbFallbackCodec codec = new JsonbFallbackCodec(
            mapper,
            context.getSerdeConfiguration().orElse(null),
            BinaryDataStrategy.BYTE
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
                JsonbRuntimeCustomizations.ConfigDeserializer configuredDeserializer = customizations.deserializer(type.asType());
                if (configuredDeserializer != null) {
                    try (JsonParser parser = JsonbJsonpBridge.parserForDeserializer(decoder)) {
                        return configuredDeserializer.deserializer().deserialize(parser, new JsonbDeserializationContext(codec), type.asType());
                    } catch (JsonbException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        throw new JsonbException("Cannot deserialize JSON-B value with configured deserializer", e);
                    }
                }
                if (Collection.class.isAssignableFrom(type.getType()) && type.getTypeParameters().length > 0) {
                    return deserializeCollection(decoder, customizations, type, codec);
                }
                JsonNode value = decoder.decodeNode();
                JsonbRuntimeCustomizations.ConfigAdapter adapter = customizations.adapter(type.asType());
                if (adapter != null) {
                    Object adapted = codec.readValue(value, adapter.targetType());
                    try {
                        return adapter.adapter().adaptFromJson(adapted);
                    } catch (Exception e) {
                        throw new JsonbException("Cannot adapt JSON-B value for deserialization", e);
                    }
                }
                throw new JsonbException("No configured JSON-B deserializer for type " + type.getType().getName());
            }
        };
    }

    private static Collection<?> deserializeCollection(Decoder decoder,
                                                       JsonbRuntimeCustomizations customizations,
                                                       Argument<?> type,
                                                       JsonbFallbackCodec codec) throws IOException {
        Decoder array = decoder.decodeArray(type);
        Argument<?> elementType = type.getTypeParameters()[0];
        List<@Nullable Object> result = new ArrayList<>();
        while (array.hasNextArrayValue()) {
            if (array.decodeNull()) {
                result.add(null);
                continue;
            }
            JsonbRuntimeCustomizations.ConfigDeserializer configuredDeserializer = customizations.deserializer(elementType.asType());
            if (configuredDeserializer != null) {
                try (JsonParser parser = JsonbJsonpBridge.parserForDeserializer(array)) {
                    result.add(configuredDeserializer.deserializer().deserialize(parser, new JsonbDeserializationContext(codec), elementType.asType()));
                    continue;
                } catch (JsonbException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw new JsonbException("Cannot deserialize JSON-B collection element with configured deserializer", e);
                }
            }
            JsonNode item = array.decodeNode();
            JsonbRuntimeCustomizations.ConfigAdapter adapter = customizations.adapter(elementType.asType());
            if (adapter != null) {
                Object adapted = codec.readValue(item, adapter.targetType());
                try {
                    result.add(adapter.adapter().adaptFromJson(adapted));
                    continue;
                } catch (Exception e) {
                    throw new JsonbException("Cannot adapt JSON-B collection element for deserialization", e);
                }
            }
            result.add(codec.readValue(item, elementType));
        }
        array.finishStructure();
        return result;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Object> type, Object value) throws IOException {
        createSpecific(context, type).serialize(encoder, context, type, value);
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return createSpecific(context, type).deserialize(decoder, context, type);
    }

    private static JsonbRuntimeCustomizations customizations(Argument<?> type) throws SerdeException {
        String id = type.getAnnotationMetadata().stringValue(JsonbSerdeConfig.class, "customization")
            .orElseThrow(() -> new SerdeException("Missing JSON-B configured customization metadata"));
        return JsonbRuntimeCustomizations.get(id);
    }
}
