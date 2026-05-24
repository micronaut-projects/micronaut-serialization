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
package io.micronaut.serde.toml;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.toml.serde.TomlTreeEncoder;
import io.micronaut.serde.toml.support.MicronautTomlParserAdapter;
import io.micronaut.serde.toml.support.SerdeTomlConfiguration;
import io.micronaut.serde.toml.support.TomlDecoderContext;
import io.micronaut.serde.toml.support.TomlEncoderContext;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A TOML-backed {@link ObjectMapper}.
 *
 * @author Mousrij Hamza
 */
@Singleton
@Named("toml")
@Internal
public final class TomlObjectMapper implements ObjectMapper {

    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeTomlConfiguration tomlConfiguration;
    private final MicronautTomlParserAdapter parserAdapter;

    public TomlObjectMapper(SerdeRegistry registry,
                            @Nullable SerdeConfiguration serdeConfiguration,
                            SerdeTomlConfiguration tomlConfiguration) {
        this(
            registry,
            serdeConfiguration,
            tomlConfiguration,
            new MicronautTomlParserAdapter(serdeConfiguration, tomlConfiguration)
        );
    }

    private TomlObjectMapper(SerdeRegistry registry,
                             @Nullable SerdeConfiguration serdeConfiguration,
                             SerdeTomlConfiguration tomlConfiguration,
                             MicronautTomlParserAdapter parserAdapter) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.tomlConfiguration = tomlConfiguration;
        this.parserAdapter = parserAdapter;
        //tomlConfiguration.getResolvedWriteLayout();
    }

    @Override
    public @NonNull SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    public <T> @Nullable T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        JsonNode tree = parserAdapter.parse(inputStream);
        Deserializer.DecoderContext decoderContext = new TomlDecoderContext(registry, null, limits(), tomlConfiguration);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        return deserializer.deserializeNullable(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    @Override
    public <T> @Nullable T readValue(byte @NonNull [] byteArray, @NonNull Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
    }

    @Override
    public <T> @Nullable T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = new TomlDecoderContext(registry, null, limits(), tomlConfiguration);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        return deserializer.deserializeNullable(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        TomlTreeEncoder encoder = TomlTreeEncoder.create(limits(), tomlConfiguration);
        serialize(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public @NonNull <T> JsonNode writeValueToTree(@NonNull Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        TomlTreeEncoder encoder = TomlTreeEncoder.create(limits(), tomlConfiguration);
        serialize(encoder, value, type);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        TomlGeneratorEncoder encoder = TomlGeneratorEncoder.create(outputStream, limits(), tomlConfiguration);
        if (object == null) {
            encoder.encodeNull();
        } else {
            serialize(encoder, object);
        }
        encoder.writeCompleted();
    }

    @Override
    public <T> void writeValue(@NonNull OutputStream outputStream, @NonNull Argument<T> type, @Nullable T object) throws IOException {
        TomlGeneratorEncoder encoder = TomlGeneratorEncoder.create(outputStream, limits(), tomlConfiguration);
        if (object == null) {
            encoder.encodeNull();
        } else {
            serialize(encoder, object, type);
        }
        encoder.writeCompleted();
    }

    @Override
    public byte @NonNull [] writeValueAsBytes(@Nullable Object object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public <T> byte @NonNull [] writeValueAsBytes(@NonNull Argument<T> type, @Nullable T object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    @Override
    public @NonNull JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    @Override
    public @NonNull ObjectMapper cloneWithConfiguration(@Nullable SerdeConfiguration configuration,
                                                        @Nullable SerializationConfiguration serializationConfiguration,
                                                        @Nullable DeserializationConfiguration deserializationConfiguration) {
        SerdeConfiguration actualConfiguration = configuration == null ? this.serdeConfiguration : configuration;
        SerdeRegistry actualRegistry = registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration);
        return new TomlObjectMapper(
            actualRegistry,
            actualConfiguration,
            tomlConfiguration,
            new MicronautTomlParserAdapter(actualConfiguration, tomlConfiguration)
        );
    }

    private LimitingStream.@NonNull RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void serialize(@NonNull Encoder encoder, @NonNull Object value) throws IOException {
        serialize(encoder, value, (Argument) Argument.of(value.getClass()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> void serialize(@NonNull Encoder encoder, @NonNull T value, @NonNull Argument<T> type) throws IOException {
        Serializer.EncoderContext encoderContext = new TomlEncoderContext(registry, null);
        Serializer serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type);
        serializer.serialize(encoder, encoderContext, type, value);
    }
}
