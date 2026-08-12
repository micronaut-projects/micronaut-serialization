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
package io.micronaut.serde.cbor;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.jackson.JacksonEncoder;
import io.micronaut.serde.support.util.BufferingJsonNodeProcessor;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.support.util.JsonViewUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORFactoryBuilder;
import tools.jackson.dataformat.cbor.CBORReadFeature;
import tools.jackson.dataformat.cbor.CBORWriteFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@link ObjectMapper} implementation that encodes and decodes CBOR using Jackson's streaming
 * CBOR factory together with Micronaut Serialization serializers and deserializers.
 *
 * <p>This mapper does <strong>not</strong> use Jackson Databind. {@link CBORFactory} is used only
 * as a token parser/generator; object graphs are handled by {@link SerdeRegistry}.</p>
 *
 * @since 3.1.0
 */
@Singleton
@BootstrapContextCompatible
@Order(150) // lower precedence than Jackson JSON (@Primary) and BSON JSON
public final class CborObjectMapper implements ObjectMapper {

    private final SerdeRegistry registry;
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeCborConfiguration cborConfiguration;
    private final CBORFactory cborFactory;
    private final LimitingStream.RemainingLimits streamLimits;
    @Nullable
    private final Class<?> view;
    private final Serializer.EncoderContext encoderContext;
    private final Deserializer.DecoderContext decoderContext;
    @Nullable
    private final Argument<?> specificType;
    @Nullable
    private final Deserializer<?> specificDeserializer;
    @Nullable
    private final Serializer<?> specificSerializer;

    /**
     * Creates a CBOR mapper backed by the given registry and configuration.
     *
     * @param registry           The serde registry
     * @param serdeConfiguration The serde configuration
     * @param cborConfiguration  The CBOR configuration
     */
    @Inject
    public CborObjectMapper(SerdeRegistry registry,
                            SerdeConfiguration serdeConfiguration,
                            SerdeCborConfiguration cborConfiguration) {
        this(
            CborSerdeConfigurationSupport.registryForCbor(registry, serdeConfiguration, cborConfiguration),
            CborSerdeConfigurationSupport.withWriteBinaryAsArray(serdeConfiguration, cborConfiguration.isWriteBinaryAsArray()),
            cborConfiguration,
            buildCborFactory(cborConfiguration),
            null,
            null,
            null,
            null
        );
    }

    private CborObjectMapper(SerdeRegistry registry,
                             SerdeConfiguration serdeConfiguration,
                             SerdeCborConfiguration cborConfiguration,
                             CBORFactory cborFactory,
                             @Nullable Class<?> view,
                             @Nullable Argument<?> specificType,
                             @Nullable Deserializer<?> specificDeserializer,
                             @Nullable Serializer<?> specificSerializer) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.cborConfiguration = cborConfiguration;
        this.view = view;
        this.encoderContext = registry.newEncoderContext(view);
        this.decoderContext = registry.newDecoderContext(view);
        // the factory is thread-safe and configuration-derived, so clones share it
        this.cborFactory = cborFactory;
        this.streamLimits = LimitingStream.limitsFromConfiguration(serdeConfiguration);
        this.specificType = specificType;
        this.specificDeserializer = specificDeserializer;
        this.specificSerializer = specificSerializer;
    }

    private static CBORFactory buildCborFactory(SerdeCborConfiguration cborConfiguration) {
        CBORFactoryBuilder builder = CBORFactory.builder()
            .recyclerPool(JsonRecyclerPools.threadLocalPool());
        for (Map.Entry<CBORReadFeature, Boolean> e : cborConfiguration.getCborReadFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<CBORWriteFeature, Boolean> e : cborConfiguration.getCborWriteFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<StreamReadFeature, Boolean> e : cborConfiguration.getStreamReadFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<StreamWriteFeature, Boolean> e : cborConfiguration.getStreamWriteFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        return builder.build();
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    public JsonMapper createSpecific(Argument<?> type) {
        CborObjectMapper mapper;
        try {
            mapper = new CborObjectMapper(
                registry,
                serdeConfiguration,
                cborConfiguration,
                cborFactory,
                view,
                type,
                registry.findDeserializer(type).createSpecific(decoderContext, (Argument) type),
                registry.findSerializer(type).createSpecific(encoderContext, (Argument) type)
            );
        } catch (Exception e) {
            // In a case of unknown type return this non-specific mapper
            mapper = this;
        }
        @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
        if (viewClass != view) {
            return new CborObjectMapper(
                registry,
                serdeConfiguration,
                cborConfiguration,
                cborFactory,
                viewClass,
                mapper.specificType,
                mapper.specificDeserializer,
                mapper.specificSerializer
            );
        }
        return mapper;
    }

    @Override
    public ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration,
                                               @Nullable SerializationConfiguration serializationConfiguration,
                                               @Nullable DeserializationConfiguration deserializationConfiguration) {
        return cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration, null);
    }

    @Override
    public ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration,
                                               @Nullable SerializationConfiguration serializationConfiguration,
                                               @Nullable DeserializationConfiguration deserializationConfiguration,
                                               @Nullable SerdeIntrospections introspections) {
        SerdeConfiguration base = configuration == null ? this.serdeConfiguration : configuration;
        SerdeConfiguration effective = CborSerdeConfigurationSupport.withWriteBinaryAsArray(
            base,
            cborConfiguration.isWriteBinaryAsArray()
        );
        SerdeRegistry cloned = introspections == null
            ? registry.cloneWithConfiguration(effective, serializationConfiguration, deserializationConfiguration)
            : registry.cloneWithConfiguration(effective, serializationConfiguration, deserializationConfiguration, introspections);
        return new CborObjectMapper(
            cloned,
            effective,
            cborConfiguration,
            cborFactory,
            view,
            specificType,
            specificDeserializer,
            specificSerializer
        );
    }

    /**
     * Create a new mapper with the given CBOR configuration.
     *
     * @param cborConfiguration The CBOR configuration
     * @return A new mapper
     */
    public CborObjectMapper cloneWithConfiguration(SerdeCborConfiguration cborConfiguration) {
        SerdeConfiguration effective = CborSerdeConfigurationSupport.withWriteBinaryAsArray(
            serdeConfiguration,
            cborConfiguration.isWriteBinaryAsArray()
        );
        return new CborObjectMapper(
            registry.cloneWithConfiguration(effective, null, null),
            effective,
            cborConfiguration,
            buildCborFactory(cborConfiguration),
            view,
            specificType,
            specificDeserializer,
            specificSerializer
        );
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new CborObjectMapper(
            registry,
            serdeConfiguration,
            cborConfiguration,
            cborFactory,
            viewClass,
            specificType,
            specificDeserializer,
            specificSerializer
        );
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeValue0(JsonGenerator gen, Object value) throws IOException {
        writeValue(gen, value, (Class) value.getClass());
    }

    private <T> void writeValue(JsonGenerator gen, T value, Class<T> type) throws IOException {
        writeValue(gen, value, Argument.of(type));
    }

    private <T> void writeValue(JsonGenerator gen, T value, Argument<T> argument) throws IOException {
        Serializer<? super T> serializer;
        Serializer.EncoderContext encoderContext = this.encoderContext;
        if (isSpecificType(argument)) {
            serializer = (Serializer<? super T>) Objects.requireNonNull(specificSerializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, argument, view);
            if (viewClass != view) {
                encoderContext = registry.newEncoderContext(viewClass);
            }
            serializer = encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
        }
        final Encoder encoder = JacksonEncoder.create(gen, streamLimits);
        serializer.serialize(encoder, encoderContext, argument, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> @Nullable T readValue(JsonParser parser, Argument<T> type) throws IOException {
        Deserializer deserializer;
        Deserializer.DecoderContext decoderContext = this.decoderContext;
        if (isSpecificType(type)) {
            deserializer = Objects.requireNonNull(specificDeserializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
            if (viewClass != view) {
                decoderContext = registry.newDecoderContext(viewClass);
            }
            deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
        }
        final Decoder decoder = JacksonDecoder.create(parser, streamLimits);
        return (T) deserializer.deserializeNullable(decoder, decoderContext, type);
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer deserializer;
        Deserializer.DecoderContext decoderContext = this.decoderContext;
        if (isSpecificType(type)) {
            deserializer = Objects.requireNonNull(specificDeserializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
            if (viewClass != view) {
                decoderContext = registry.newDecoderContext(viewClass);
            }
            deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        }
        return (T) deserializer.deserializeNullable(JsonNodeDecoder.create(tree, streamLimits), decoderContext, type);
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(streamLimits);
        writeWithEncoder(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(streamLimits);
        writeWithEncoder(encoder, value, type);
        return encoder.getCompletedValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeWithEncoder(Encoder encoder, Object value) throws IOException {
        writeWithEncoder(encoder, value, (Argument) Argument.of(value.getClass()));
    }

    private <T> void writeWithEncoder(Encoder encoder, T value, Argument<T> type) throws IOException {
        Serializer<? super T> serializer;
        Serializer.EncoderContext encoderContext = this.encoderContext;
        if (isSpecificType(type)) {
            serializer = (Serializer<? super T>) Objects.requireNonNull(specificSerializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
            if (viewClass != view) {
                encoderContext = registry.newEncoderContext(viewClass);
            }
            serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type);
        }
        serializer.serialize(encoder, encoderContext, type, value);
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try (JsonParser parser = cborFactory.createParser(inputStream)) {
            return readValue(parser, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (JsonParser parser = cborFactory.createParser(byteArray)) {
            return readValue(parser, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> @Nullable T readValue(ByteBuffer<?> byteBuffer, Argument<T> type) throws IOException {
        return readValue(byteBuffer.toByteArray(), type);
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        try (JsonGenerator generator = cborFactory.createGenerator(outputStream)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue0(generator, object);
            }
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        try (JsonGenerator generator = cborFactory.createGenerator(outputStream)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue(generator, object, type);
            }
        }
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        BufferRecycler bufferRecycler = cborFactory._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(bufferRecycler)) {
            try (JsonGenerator generator = cborFactory.createGenerator(bb)) {
                if (object == null) {
                    generator.writeNull();
                } else {
                    writeValue0(generator, object);
                }
            }
            return bb.getClearAndRelease();
        } finally {
            bufferRecycler.releaseToPool();
        }
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        BufferRecycler bufferRecycler = cborFactory._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(bufferRecycler)) {
            try (JsonGenerator generator = cborFactory.createGenerator(bb)) {
                if (object == null) {
                    generator.writeNull();
                } else {
                    writeValue(generator, object, type);
                }
            }
            return bb.getClearAndRelease();
        } finally {
            bufferRecycler.releaseToPool();
        }
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        return new BufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @Override
            protected JsonNode parseOne(InputStream is) throws IOException {
                try (JsonParser parser = cborFactory.createParser(is)) {
                    return JacksonDecoder.create(parser, streamLimits).decodeNode();
                }
            }

            @Override
            protected JsonNode parseOne(byte[] remaining) throws IOException {
                try (JsonParser parser = cborFactory.createParser(remaining)) {
                    return JacksonDecoder.create(parser, streamLimits).decodeNode();
                }
            }
        };
    }

    @Override
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        if (tree != null && value != null) {
            @SuppressWarnings("unchecked")
            Argument<Object> type = (Argument<Object>) Argument.of(value.getClass());
            updateValueFromNode(value, type, tree);
        }
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, @Nullable Object overrides) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (overrides == null) {
            return valueToUpdate;
        }
        if (overrides instanceof JsonNode jsonNode) {
            return updateValueFromNode(valueToUpdate, type, jsonNode);
        }
        BufferRecycler bufferRecycler = cborFactory._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(bufferRecycler)) {
            try (JsonGenerator generator = cborFactory.createGenerator(bb)) {
                writeValue0(generator, overrides);
            }
            try (JsonParser parser = cborFactory.createParser(bb.getClearAndRelease())) {
                return updateValue(parser, valueToUpdate, type);
            }
        } finally {
            bufferRecycler.releaseToPool();
        }
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, InputStream inputStream) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        try (JsonParser parser = cborFactory.createParser(inputStream)) {
            return updateValue(parser, valueToUpdate, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, byte[] byteArray) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(byteArray, "Byte array cannot be null");
        try (JsonParser parser = cborFactory.createParser(byteArray)) {
            return updateValue(parser, valueToUpdate, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, ByteBuffer<?> byteBuffer) throws IOException {
        return updateValue(valueToUpdate, type, byteBuffer.toByteArray());
    }

    private <T> T updateValueFromNode(T value, Argument<T> type, JsonNode tree) throws IOException {
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (!tree.isNull()) {
            updateValue(JsonNodeDecoder.create(tree, streamLimits), value, type);
        }
        return value;
    }

    private <T> T updateValue(JsonParser parser, T value, Argument<T> type) throws IOException {
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (parser.currentToken() != JsonToken.VALUE_NULL) {
            updateValue(JacksonDecoder.create(parser, streamLimits), value, type);
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void updateValue(Decoder decoder, T value, Argument<T> type) throws IOException {
        Deserializer deserializer;
        Deserializer.DecoderContext decoderContext = this.decoderContext;
        if (isSpecificType(type)) {
            deserializer = Objects.requireNonNull(specificDeserializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
            if (viewClass != view) {
                decoderContext = registry.newDecoderContext(viewClass);
            }
            deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
        }
        if (!(deserializer instanceof UpdatingDeserializer)) {
            deserializer = decoderContext.findDeserializer(Argument.OBJECT_ARGUMENT)
                .createSpecific(decoderContext, (Argument) type);
        }
        if (!(deserializer instanceof UpdatingDeserializer updatingDeserializer)) {
            throw new UnsupportedOperationException("Updating existing value of type [" + type + "] is not supported");
        }
        updatingDeserializer.deserializeInto(decoder, decoderContext, type, value);
    }

    private boolean isSpecificType(Argument<?> type) {
        return type == specificType || type.equalsType(specificType);
    }
}
