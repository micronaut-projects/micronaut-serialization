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
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.UpdatingDeserializer;
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
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.JacksonFeature;

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
        return new JacksonJsonMapper(
            registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration),
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
        JsonFactoryBuilder builder = JsonFactory.builder();
        for (Map.Entry<JsonFactory.Feature, Boolean> e : jacksonConfiguration.getJsonFactoryFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<JsonReadFeature, Boolean> e : jacksonConfiguration.getJsonReadFeatures().entrySet()) {
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
        Serializer.EncoderContext encoderContext = this.encoderContext;
        if (argument.equalsType(specificType)) {
            serializer = (Serializer<? super T>) Objects.requireNonNull(specificSerializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, argument, view);
            if (viewClass != view) {
                encoderContext = registry.newEncoderContext(viewClass);
            }
            serializer = encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
        }
        final Encoder encoder = JacksonEncoder.create(gen, LimitingStream.limitsFromConfiguration(serdeConfiguration));
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
        Deserializer.DecoderContext decoderContext = this.decoderContext;
        if (type.equalsType(specificType)) {
            deserializer = Objects.requireNonNull(specificDeserializer);
        } else {
            @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
            if (viewClass != view) {
                decoderContext = registry.newDecoderContext(viewClass);
            }
            deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
        }
        final Decoder decoder = JacksonDecoder.create(parser, LimitingStream.limitsFromConfiguration(serdeConfiguration));
        return (T) deserializer.deserializeNullable(
            decoder,
            decoderContext,
            type
        );
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return readValue(treeCodec.treeAsTokens(tree, new ReadContextImpl()), type);
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
        try (JsonParser parser = jsonFactory.createParser(new ReadContextImpl(), inputStream)) {
            return readValue(parser, type);
        } catch (StreamReadException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (JsonParser parser = jsonFactory.createParser(new ReadContextImpl(), byteArray)) {
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
        try (JsonGenerator generator = jsonFactory.createGenerator(new WriteContextImpl(), outputStream)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue0(generator, object);
            }
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        try (JsonGenerator generator = jsonFactory.createGenerator(new WriteContextImpl(), outputStream)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue(generator, object, type);
            }
        }
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        ByteArrayBuilder bb = new ByteArrayBuilder(jsonFactory._getBufferRecycler());
        try (JsonGenerator generator = jsonFactory.createGenerator(new WriteContextImpl(), bb)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue0(generator, object);
            }
        }
        byte[] bytes = bb.toByteArray();
        bb.release();
        return bytes;
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        ByteArrayBuilder bb = new ByteArrayBuilder(jsonFactory._getBufferRecycler());
        try (JsonGenerator generator = jsonFactory.createGenerator(new WriteContextImpl(), bb)) {
            if (object == null) {
                generator.writeNull();
            } else {
                writeValue(generator, object, type);
            }
        }
        byte[] bytes = bb.toByteArray();
        bb.release();
        return bytes;
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
            Argument<Object> type = (Argument<Object>) Argument.of(value.getClass());
            Deserializer deserializer;
            if (type.equalsType(specificType)) {
                deserializer = Objects.requireNonNull(specificDeserializer);
            } else {
                deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
            }
            if (deserializer instanceof UpdatingDeserializer) {

                try (JsonParser parser = treeCodec.treeAsTokens(tree, new ReadContextImpl())) {
                    if (!parser.hasCurrentToken()) {
                        parser.nextToken();
                    }
                    // for jackson compat we need to support deserializing null, but most deserializers don't support it.
                    if (parser.currentToken() != JsonToken.VALUE_NULL) {
                        final Decoder decoder = JacksonDecoder.create(parser, LimitingStream.limitsFromConfiguration(serdeConfiguration));
                        ((UpdatingDeserializer<Object>) deserializer).deserializeInto(
                            decoder,
                            decoderContext,
                            type,
                            value
                        );
                    }
                }
            }
        }
    }

    private static int collectFeatures(int defaults, Map<? extends JacksonFeature, Boolean> config) {
        int val = defaults;
        for (Map.Entry<? extends JacksonFeature, Boolean> e : config.entrySet()) {
            if (e.getValue()) {
                val |= e.getKey().getMask();
            } else {
                val &= ~e.getKey().getMask();
            }
        }
        return val;
    }

    private final class WriteContextImpl extends ObjectWriteContext.Base {
        final int formatWriteFeatures = collectFeatures(JsonWriteFeature.collectDefaults(), jacksonConfiguration.getJsonWriteFeatures());
        final int streamWriteFeatures = collectFeatures(StreamWriteFeature.collectDefaults(), jacksonConfiguration.getStreamWriteFeatures());

        @Override
        public @Nullable PrettyPrinter getPrettyPrinter() {
            return jacksonConfiguration.isPrettyPrint() ? new DefaultPrettyPrinter() : null;
        }

        @Override
        public void writeValue(JsonGenerator g, Object value) {
            try {
                writeValue0(g, value);
            } catch (IOException e) {
                throw new StreamWriteException(g, e);
            }
        }

        @Override
        public int getFormatWriteFeatures(int defaults) {
            return formatWriteFeatures;
        }

        @Override
        public int getStreamWriteFeatures(int defaults) {
            return streamWriteFeatures;
        }
    }

    private final class ReadContextImpl extends ObjectReadContext.Base {
        final int formatReadFeatures = collectFeatures(JsonReadFeature.collectDefaults(), jacksonConfiguration.getJsonReadFeatures());
        final int streamReadFeatures = collectFeatures(StreamReadFeature.collectDefaults(), jacksonConfiguration.getStreamReadFeatures());

        @Override
        public int getFormatReadFeatures(int defaults) {
            return formatReadFeatures;
        }

        @Override
        public int getStreamReadFeatures(int defaults) {
            return streamReadFeatures;
        }
    }
}
