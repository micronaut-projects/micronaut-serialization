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
package io.micronaut.serde.json.stream;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.BufferingJsonNodeProcessor;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.support.util.JsonViewUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.reactivestreams.Processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implementation of the {@link io.micronaut.json.JsonMapper} interface for JSON-P.
 */
@Singleton
@BootstrapContextCompatible
public class JsonStreamMapper implements ObjectMapper {
    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    @Nullable
    private final Class<?> view;
    private final CoercionPolicy coercionPolicy;

    @Inject
    public JsonStreamMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration) {
        this(registry, serdeConfiguration, null);
    }

    private JsonStreamMapper(SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration, @Nullable Class<?> view) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.view = view;
        this.coercionPolicy = CoercionPolicy.fromConfiguration(
            registry.newDecoderContext(view).getDeserializationConfiguration().orElse(null));
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return this.registry;
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
        SerdeRegistry registry = introspections == null
            ? this.registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration)
            : this.registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration, introspections);
        return new JsonStreamMapper(registry, configuration == null ? serdeConfiguration : configuration, view);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new JsonStreamMapper(registry, serdeConfiguration, viewClass);
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        final Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
        try (var ignored = context.openReferenceScope()) {
            return deserializer.deserialize(
                    JsonNodeDecoder.create(tree, limits(), coercionPolicy()),
                    context,
                    type
            );
        }
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try (JsonParser parser = Json.createParser(inputStream)) {
            return readValue(parser, type);
        }
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (JsonParser parser = Json.createParser(new ByteArrayInputStream(byteArray))) {
            return readValue(parser, type);
        }
    }

    private <T> @Nullable T readValue(JsonParser parser, Argument<T> type) throws IOException {
        Decoder decoder = new JsonParserDecoder(parser, limits(), coercionPolicy());
        Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        final Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
        try (var ignored = context.openReferenceScope()) {
            return deserializer.deserialize(
                    decoder,
                    context,
                    type
            );
        }
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        return new BufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @Override
            protected JsonNode parseOne(InputStream is) throws IOException {
                try (JsonParser parser = Json.createParser(is)) {
                    final JsonParserDecoder decoder = new JsonParserDecoder(parser, limits(), coercionPolicy());
                    final Object o = decoder.decodeArbitrary();
                    return writeValueToTree(o);
                }
            }
        };
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value, type);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        try (JsonGenerator generator = Json.createGenerator(Objects.requireNonNull(outputStream, "Output stream cannot be null"))) {
            if (object == null) {
                generator.writeNull();
            } else {
                JsonStreamEncoder encoder = new JsonStreamEncoder(generator, limits());
                serialize(encoder, object);
            }
            generator.flush();
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        try (JsonGenerator generator = Json.createGenerator(Objects.requireNonNull(outputStream, "Output stream cannot be null"))) {
            if (object == null) {
                generator.writeNull();
            } else {
                JsonStreamEncoder encoder = new JsonStreamEncoder(generator, limits());
                serialize(encoder, object, type);
            }
            generator.flush();
        }
    }

    private CoercionPolicy coercionPolicy() {
        return this.coercionPolicy;
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    private void serialize(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, Argument.of(object.getClass()));
    }

    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        Serializer.EncoderContext context = registry.newEncoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        final Serializer<Object> serializer = context.findSerializer(type).createSpecific(context, type);
        serializer.serialize(
                encoder,
                context,
                type, object
        );
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

}
