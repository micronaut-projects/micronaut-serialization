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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.BufferingJsonNodeProcessor;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
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

    @Deprecated
    public JsonStreamMapper(@NonNull SerdeRegistry registry) {
        this(registry, (Class<?>) null);
    }

    @Deprecated
    public JsonStreamMapper(@NonNull SerdeRegistry registry, @Nullable Class<?> view) {
        this(registry, null, view);
    }

    @Inject
    public JsonStreamMapper(@NonNull SerdeRegistry registry, @NonNull SerdeConfiguration serdeConfiguration) {
        this(registry, serdeConfiguration, null);
    }

    private JsonStreamMapper(@NonNull SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration, @Nullable Class<?> view) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.view = view;
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return this.registry;
    }

    @Override
    public ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration, @Nullable SerializationConfiguration serializationConfiguration, @Nullable DeserializationConfiguration deserializationConfiguration) {
        return new JsonStreamMapper(registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration), configuration == null ? serdeConfiguration : configuration, view);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new JsonStreamMapper(registry, serdeConfiguration, viewClass);
    }

    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext context = registry.newDecoderContext(view);
        final Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
        return deserializer.deserialize(
                JsonNodeDecoder.create(tree, limits()),
                context,
                type
        );
    }

    @Override
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try (JsonParser parser = Json.createParser(inputStream)) {
            return readValue(parser, type);
        }
    }

    @Override
    public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (JsonParser parser = Json.createParser(new ByteArrayInputStream(byteArray))) {
            return readValue(parser, type);
        }
    }

    private <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        Decoder decoder = new JsonParserDecoder(parser, limits());
        Deserializer.DecoderContext context = registry.newDecoderContext(view);
        final Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
        return deserializer.deserialize(
                decoder,
                context,
                type
        );
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        return new BufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @NonNull
            @Override
            protected JsonNode parseOne(@NonNull InputStream is) throws IOException {
                try (JsonParser parser = Json.createParser(is)) {
                    final JsonParserDecoder decoder = new JsonParserDecoder(parser, limits());
                    final Object o = decoder.decodeArbitrary();
                    return writeValueToTree(o);
                }
            }
        };
    }

    @Override
    public JsonNode writeValueToTree(Object value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, T value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value, type);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(OutputStream outputStream, Object object) throws IOException {
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
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, T object) throws IOException {
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

    @NonNull
    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    private void serialize(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, Argument.of(object.getClass()));
    }

    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        Serializer.EncoderContext context = registry.newEncoderContext(view);
        final Serializer<Object> serializer = context.findSerializer(type).createSpecific(context, type);
        serializer.serialize(
                encoder,
                context,
                type, object
        );
    }

    @Override
    public byte[] writeValueAsBytes(Object object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, T object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

}
