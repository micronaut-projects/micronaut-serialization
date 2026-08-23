/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.jackson;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.core.parser.JacksonCoreParserFactory;
import io.micronaut.jackson.core.parser.JacksonCoreProcessor;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.jackson.core.tree.TreeGenerator;
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
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.JsonViewUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.core.util.BufferRecycler;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.JsonRecyclerPools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implementation of the {@link io.micronaut.json.JsonMapper} interface for Jackson.
 */
@Internal
@Singleton
@Primary
@BootstrapContextCompatible
public final class JacksonJsonMapper implements JacksonObjectMapper {

    private final SerdeRegistry registry;
    private final JsonStreamConfig streamConfig;
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeJacksonConfiguration jacksonConfiguration;
    private final JsonNodeTreeCodec treeCodec;
    @Nullable
    private final Class<?> view;
    private final Serializer.EncoderContext encoderContext;
    private final Deserializer.DecoderContext decoderContext;
    private final JsonFactory jsonFactory;
    private final LimitingStream.RemainingLimits streamLimits;
    private final CoercionPolicy coercionPolicy;
    @Nullable
    private final Argument<?> specificType;
    @Nullable
    private final Deserializer<?> specificDeserializer;
    @Nullable
    private final Serializer<?> specificSerializer;

    @Inject
    @Internal
    public JacksonJsonMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration, SerdeJacksonConfiguration jacksonConfiguration) {
        this(registry, JsonStreamConfig.DEFAULT, serdeConfiguration, jacksonConfiguration, Object.class, null, null, null);
    }

    private JacksonJsonMapper(SerdeRegistry registry,
                              JsonStreamConfig streamConfig,
                              SerdeConfiguration serdeConfiguration,
                              SerdeJacksonConfiguration jacksonConfiguration,
                              @Nullable Class<?> view,
                              @Nullable Argument<?> specificType,
                              @Nullable Deserializer<?> specificDeserializer,
                              @Nullable Serializer<?> serializer) {
        this.registry = registry;
        this.streamConfig = streamConfig;
        this.serdeConfiguration = serdeConfiguration;
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(streamConfig);
        this.view = view;
        this.encoderContext = registry.newEncoderContext(view);
        this.decoderContext = registry.newDecoderContext(view);
        this.jacksonConfiguration = jacksonConfiguration;
        this.jsonFactory = buildJsonFactory(jacksonConfiguration);
        this.streamLimits = LimitingStream.limitsFromConfiguration(serdeConfiguration);
        this.coercionPolicy = CoercionPolicy.fromConfiguration(this.decoderContext.getDeserializationConfiguration().orElse(null));
        this.specificType = specificType;
        this.specificDeserializer = specificDeserializer;
        this.specificSerializer = serializer;
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return this.registry;
    }

    @Override
    public JsonMapper createSpecific(Argument<?> type) {
        JacksonJsonMapper mapper;
        try {
            mapper = new JacksonJsonMapper(
                registry,
                streamConfig,
                serdeConfiguration,
                jacksonConfiguration,
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
            return new JacksonJsonMapper(registry, streamConfig, serdeConfiguration, jacksonConfiguration, viewClass, specificType, specificDeserializer, specificSerializer);
        }
        return mapper;
    }

    @Override
    public ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration, @Nullable SerializationConfiguration serializationConfiguration, @Nullable DeserializationConfiguration deserializationConfiguration) {
        return cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration, null);
    }

    @Override
    public ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration,
                                               @Nullable SerializationConfiguration serializationConfiguration,
                                               @Nullable DeserializationConfiguration deserializationConfiguration,
                                               @Nullable SerdeIntrospections introspections) {
        return new JacksonJsonMapper(
            introspections == null
                ? registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration)
                : registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration, introspections),
            streamConfig,
            configuration == null ? this.serdeConfiguration : configuration,
            jacksonConfiguration,
            view,
            specificType,
            specificDeserializer,
            specificSerializer
        );
    }

    @Override
    public JacksonObjectMapper cloneWithConfiguration(SerdeJacksonConfiguration jacksonConfiguration) {
        return new JacksonJsonMapper(
            registry,
            streamConfig,
            serdeConfiguration,
            jacksonConfiguration,
            view,
            specificType,
            specificDeserializer,
            specificSerializer
        );
    }

    private static JsonFactory buildJsonFactory(SerdeJacksonConfiguration jacksonConfiguration) {
        JsonFactoryBuilder builder = JsonFactory.builder()
            .recyclerPool(JsonRecyclerPools.threadLocalPool());
        for (Map.Entry<JsonFactory.Feature, Boolean> e : jacksonConfiguration.getJsonFactoryFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<JsonReadFeature, Boolean> e : jacksonConfiguration.getJsonReadFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<StreamReadFeature, Boolean> e : jacksonConfiguration.getStreamReadFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<JsonWriteFeature, Boolean> e : jacksonConfiguration.getJsonWriteFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<StreamWriteFeature, Boolean> e : jacksonConfiguration.getStreamWriteFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeValue0(JsonGenerator gen, Object value) throws IOException {
        writeValue0(gen, value, (Class) value.getClass());
    }

    // type-safe helper method
    private <T> void writeValue0(JsonGenerator gen, T value, Class<T> type) throws IOException {
        writeValue(gen, value, Argument.of(type));
    }

    private <T> void writeValue(JsonGenerator gen, T value, Argument<T> argument) throws IOException {
        Serializer<? super T> serializer;
        // Each document gets its own context so managed references and object identities do not leak between documents
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(JsonViewUtil.extractView(serdeConfiguration, argument, view));
        if (isSpecificType(argument)) {
            serializer = (Serializer<? super T>) Objects.requireNonNull(specificSerializer);
        } else {
            serializer = encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
        }
        final Encoder encoder = JacksonEncoder.create(gen, streamLimits);
        serializer.serialize(
            encoder,
            encoderContext,
            argument, value
        );
    }

    private <T> @Nullable T readValue(JsonParser parser, Argument<T> type) throws IOException {
        return readValue0(parser, type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> @Nullable T readValue0(JsonParser parser, Argument<?> type) throws IOException {
        Deserializer deserializer;
        // Each document gets its own context so managed references and object identities do not leak between documents
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        if (isSpecificType(type)) {
            deserializer = Objects.requireNonNull(specificDeserializer);
        } else {
            deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
        }
        final Decoder decoder = createDecoder(parser);
        try (var ignored = decoderContext.openReferenceScope()) {
            return (T) deserializer.deserializeNullable(
                decoder,
                decoderContext,
                type
            );
        }
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return readValue(treeCodec.treeAsTokens(tree, ObjectReadContext.empty()), type);
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        writeValue0(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        writeValue(treeGenerator, value, type);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try (JsonParser parser = jsonFactory.createParser(inputStream)) {
            return readValue(parser, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (JsonParser parser = jsonFactory.createParser(byteArray)) {
            return readValue(parser, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> @Nullable T readValue(ByteBuffer<?> byteBuffer, Argument<T> type) throws IOException {
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(jsonFactory, byteBuffer)) {
            return readValue(parser, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        try (JsonGenerator generator = createGenerator(outputStream)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue0(generator, object);
            }
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        try (JsonGenerator generator = createGenerator(outputStream)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue(generator, object, type);
            }
        }
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(bufferRecycler)) {
            try (JsonGenerator generator = createGenerator(bb)) {
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
        BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(bufferRecycler)) {
            try (JsonGenerator generator = createGenerator(bb)) {
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
    public JsonStreamConfig getStreamConfig() {
        return streamConfig;
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, jsonFactory, streamConfig) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new JacksonJsonMapper(registry, streamConfig, serdeConfiguration, jacksonConfiguration, viewClass, specificType, specificDeserializer, specificSerializer);
    }

    @Override
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        if (tree != null && value != null) {
            try (JsonParser parser = treeCodec.treeAsTokens(tree, ObjectReadContext.empty())) {
                updateValue(parser, value, (Argument<Object>) Argument.of(value.getClass()));
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T updateValue(T valueToUpdate, @Nullable Object overrides) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        return updateValue(valueToUpdate, (Argument<T>) Argument.of(valueToUpdate.getClass()), overrides);
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, @Nullable Object overrides) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (overrides == null) {
            return valueToUpdate;
        }
        if (overrides instanceof JsonNode jsonNode) {
            try (JsonParser parser = treeCodec.treeAsTokens(jsonNode, ObjectReadContext.empty())) {
                return updateValue(parser, valueToUpdate, type);
            }
        }
        BufferRecycler bufferRecycler = jsonFactory._getBufferRecycler();
        try (ByteArrayBuilder bb = new ByteArrayBuilder(bufferRecycler)) {
            try (JsonGenerator generator = createGenerator(bb)) {
                writeValue0(generator, overrides);
            }
            try (JsonParser parser = jsonFactory.createParser(bb.getClearAndRelease())) {
                updateValue(parser, valueToUpdate, type);
            }
        } finally {
            bufferRecycler.releaseToPool();
        }
        return valueToUpdate;
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, InputStream inputStream) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        try (JsonParser parser = jsonFactory.createParser(inputStream)) {
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
        try (JsonParser parser = jsonFactory.createParser(byteArray)) {
            return updateValue(parser, valueToUpdate, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, ByteBuffer<?> byteBuffer) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(byteBuffer, "Byte buffer cannot be null");
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(jsonFactory, byteBuffer)) {
            return updateValue(parser, valueToUpdate, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    private <T> T updateValue(JsonParser parser, T value, Argument<T> type) throws IOException {
        Deserializer<T> deserializer;
        // Each document gets its own context so managed references and object identities do not leak between documents
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        if (isSpecificType(type)) {
            deserializer = (Deserializer<T>) Objects.requireNonNull(specificDeserializer);
        } else {
            deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
        }
        if (!(deserializer instanceof UpdatingDeserializer<T>)) {
            deserializer = decoderContext.findDeserializer(Argument.OBJECT_ARGUMENT)
                .createSpecific(decoderContext, (Argument) type);
        }
        if (!(deserializer instanceof UpdatingDeserializer<T> updatingDeserializer)) {
            throw new UnsupportedOperationException("Updating existing value of type [" + type + "] is not supported");
        }
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (parser.currentToken() != JsonToken.VALUE_NULL) {
            final Decoder decoder = createDecoder(parser);
            try (var ignored = decoderContext.openReferenceScope()) {
                updatingDeserializer.deserializeInto(
                    decoder,
                    decoderContext,
                    type,
                    value
                );
            }
        }
        return value;
    }

    private Decoder createDecoder(JsonParser parser) throws IOException {
        return JacksonDecoder.create(parser, streamLimits, coercionPolicy);
    }

    private boolean isSpecificType(Argument<?> type) {
        return type == specificType || type.equalsType(specificType);
    }

    private JsonGenerator createGenerator(OutputStream outputStream) {
        if (jacksonConfiguration.isPrettyPrint()) {
            return jsonFactory.createGenerator(new PrettyPrintWriteContext(), outputStream);
        }
        return jsonFactory.createGenerator(outputStream);
    }

    private final class PrettyPrintWriteContext extends ObjectWriteContext.Base {
        @Override
        public PrettyPrinter getPrettyPrinter() {
            return new DefaultPrettyPrinter();
        }

        @Override
        public void writeValue(JsonGenerator g, Object value) {
            try {
                writeValue0(g, value);
            } catch (IOException e) {
                throw new StreamWriteException(g, e);
            }
        }
    }
}
