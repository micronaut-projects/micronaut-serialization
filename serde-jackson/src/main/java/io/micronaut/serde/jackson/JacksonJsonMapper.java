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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.TSFBuilder;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
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
    private final Class<?> view;
    private final ObjectCodecImpl objectCodecImpl = new ObjectCodecImpl();
    private final Serializer.EncoderContext encoderContext;
    private final Deserializer.DecoderContext decoderContext;
    private final JsonFactory jsonFactory;

    @Inject
    @Internal
    public JacksonJsonMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration, SerdeJacksonConfiguration jacksonConfiguration) {
        this(registry, JsonStreamConfig.DEFAULT, serdeConfiguration, jacksonConfiguration, Object.class);
    }

    private JacksonJsonMapper(@NonNull SerdeRegistry registry,
                              @NonNull JsonStreamConfig streamConfig,
                              @NonNull SerdeConfiguration serdeConfiguration,
                              @NonNull SerdeJacksonConfiguration jacksonConfiguration,
                              @Nullable Class<?> view) {
        this.registry = registry;
        this.streamConfig = streamConfig;
        this.serdeConfiguration = serdeConfiguration;
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(streamConfig);
        this.view = view;
        this.encoderContext = registry.newEncoderContext(view);
        this.decoderContext = registry.newDecoderContext(view);
        this.jacksonConfiguration = jacksonConfiguration;
        this.jsonFactory = buildJsonFactory(jacksonConfiguration);
    }

    @Override
    public ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration, @Nullable SerializationConfiguration serializationConfiguration, @Nullable DeserializationConfiguration deserializationConfiguration) {
        return new JacksonJsonMapper(
            registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration),
            streamConfig,
            configuration == null ? this.serdeConfiguration : configuration,
            jacksonConfiguration,
            view
        );
    }

    @Override
    public JacksonObjectMapper cloneWithConfiguration(SerdeJacksonConfiguration jacksonConfiguration) {
        return new JacksonJsonMapper(
            registry,
            streamConfig,
            serdeConfiguration,
            jacksonConfiguration,
            view
        );
    }

    private static JsonFactory buildJsonFactory(SerdeJacksonConfiguration jacksonConfiguration) {
        TSFBuilder<?, ?> builder = JsonFactory.builder();
        for (Map.Entry<JsonFactory.Feature, Boolean> e : jacksonConfiguration.getFactoryFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<JsonReadFeature, Boolean> e : jacksonConfiguration.getReadFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<JsonWriteFeature, Boolean> e : jacksonConfiguration.getWriteFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        for (Map.Entry<StreamWriteFeature, Boolean> e : jacksonConfiguration.getStreamFeatures().entrySet()) {
            builder = builder.configure(e.getKey(), e.getValue());
        }
        return builder.build();
    }

    private void configureGenerator(JsonGenerator generator) {
        generator.setCodec(objectCodecImpl);
        for (Map.Entry<JsonGenerator.Feature, Boolean> e : jacksonConfiguration.getGeneratorFeatures().entrySet()) {
            generator.configure(e.getKey(), e.getValue());
        }
        if (jacksonConfiguration.isPrettyPrint()) {
            generator.setPrettyPrinter(new DefaultPrettyPrinter());
        }
    }

    private void configureParser(JsonParser parser) {
        parser.setCodec(objectCodecImpl);
        for (Map.Entry<JsonParser.Feature, Boolean> e : jacksonConfiguration.getParserFeatures().entrySet()) {
            parser.configure(e.getKey(), e.getValue());
        }
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
        configureGenerator(gen);

        Serializer<? super T> serializer = encoderContext.findSerializer(argument)
                                                   .createSpecific(encoderContext, argument);
        final Encoder encoder = JacksonEncoder.create(gen, LimitingStream.limitsFromConfiguration(serdeConfiguration));
        serializer.serialize(
                encoder,
                encoderContext,
                argument, value
        );
    }

    private <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        return readValue0(parser, type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T readValue0(JsonParser parser, Argument<?> type) throws IOException {
        configureParser(parser);
        Deserializer deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type);
        final Decoder decoder = JacksonDecoder.create(parser, LimitingStream.limitsFromConfiguration(serdeConfiguration));
        return (T) deserializer.deserializeNullable(
                decoder,
                decoderContext,
                type
        );
    }

    @Override
    public <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        return readValue(treeCodec.treeAsTokens(tree), type);
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        writeValue0(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, T value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        writeValue(treeGenerator, value, type);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        try (JsonParser parser = jsonFactory.createParser(inputStream)) {
            return readValue(parser, type);
        } catch (JsonParseException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T readValue(@NonNull byte[] byteArray, @NonNull Argument<T> type) throws IOException {
        try (JsonParser parser = jsonFactory.createParser(byteArray)) {
            return readValue(parser, type);
        } catch (JsonParseException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public <T> T readValue(@NonNull ByteBuffer<?> byteBuffer, @NonNull Argument<T> type) throws IOException {
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(jsonFactory, byteBuffer)) {
            return readValue(parser, type);
        } catch (JsonParseException pe) {
            throw new JsonSyntaxException(pe);
        }
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        try (JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {
            writeValue0(generator, object);
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, T object) throws IOException {
        try (JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {
            writeValue(generator, object, type);
        }
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        ByteArrayBuilder bb = new ByteArrayBuilder(jsonFactory._getBufferRecycler());
        try (JsonGenerator generator = jsonFactory.createGenerator(bb)) {
            writeValue0(generator, object);
        }
        byte[] bytes = bb.toByteArray();
        bb.release();
        return bytes;
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, T object) throws IOException {
        ByteArrayBuilder bb = new ByteArrayBuilder(jsonFactory._getBufferRecycler());
        try (JsonGenerator generator = jsonFactory.createGenerator(bb)) {
            writeValue(generator, object, type);
        }
        byte[] bytes = bb.toByteArray();
        bb.release();
        return bytes;
    }

    @NonNull
    @Override
    public JsonStreamConfig getStreamConfig() {
        return streamConfig;
    }

    @Override
    public @NonNull Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, jsonFactory, streamConfig) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
    }

    @NonNull
    @Override
    public JsonMapper cloneWithViewClass(@NonNull Class<?> viewClass) {
        return new JacksonJsonMapper(registry, streamConfig, serdeConfiguration, jacksonConfiguration, viewClass);
    }

    @Override
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        if (tree != null && value != null) {
            Argument<Object> type = (Argument<Object>) Argument.of(value.getClass());
            Deserializer deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
            if (deserializer instanceof UpdatingDeserializer) {

                try (JsonParser parser = treeCodec.treeAsTokens(tree)) {
                    configureParser(parser);
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

    private class ObjectCodecImpl extends ObjectCodec {
        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public <T> T readValue(JsonParser p, Class<T> valueType) throws IOException {
            return readValue0(p, Argument.of(valueType));
        }

        @Override
        public <T> T readValue(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
            return readValue0(p, Argument.of(valueTypeRef.getType()));
        }

        @Override
        public <T> T readValue(JsonParser p, ResolvedType valueType) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, Class<T> valueType) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, ResolvedType valueType) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeValue(JsonGenerator gen, Object value) throws IOException {
            writeValue0(gen, value);
        }

        @Override
        public <T extends TreeNode> T readTree(JsonParser p) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTree(JsonGenerator gen, TreeNode tree) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public TreeNode createObjectNode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TreeNode createArrayNode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonParser treeAsTokens(TreeNode n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T treeToValue(TreeNode n, Class<T> valueType) throws JsonProcessingException {
            throw new UnsupportedOperationException();
        }
    }
}
