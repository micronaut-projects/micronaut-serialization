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
package io.micronaut.serde.yaml;

import io.micronaut.context.annotation.Secondary;
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
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.support.util.JsonViewUtil;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * YAML-backed {@link ObjectMapper}.
 *
 * <p>The mapper uses the event based parser and emitter of {@code snakeyaml-engine} only as a
 * token source and sink; object graphs are mapped by the Micronaut Serialization serializers and
 * deserializers resolved through the {@link SerdeRegistry}. Inject it with the {@link #YAML}
 * qualifier, or by its concrete type, to read and write YAML next to the primary JSON mapper.</p>
 *
 * @since 3.2.0
 * @author Mohamed Chbani
 * @author Hamza Mousrij
 */
@Named(YamlObjectMapper.YAML)
@Secondary
@Singleton
public final class YamlObjectMapper implements ObjectMapper {

    /**
     * The qualifier name of the YAML {@link ObjectMapper} bean.
     */
    public static final String YAML = "yaml";

    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeYamlConfiguration yamlConfiguration;
    private final YamlStringQuotingChecker quotingChecker;
    @Nullable
    private final Class<?> view;
    private final LimitingStream.RemainingLimits streamLimits;
    private final CoercionPolicy coercionPolicy;
    private final YamlReadSettings readSettings;

    /**
     * Creates a YAML-backed {@link ObjectMapper}.
     *
     * @param registry The serde registry used to resolve serializers and deserializers
     * @param serdeConfiguration The serde configuration, when available
     * @param yamlConfiguration The YAML configuration
     * @param quotingChecker The YAML string quoting checker
     * @param view The active serialization view, when available
     */
    @Inject
    public YamlObjectMapper(SerdeRegistry registry,
                            @Nullable SerdeConfiguration serdeConfiguration,
                            SerdeYamlConfiguration yamlConfiguration,
                            YamlStringQuotingChecker quotingChecker,
                            @Nullable Class<?> view) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.serdeConfiguration = serdeConfiguration;
        this.yamlConfiguration = Objects.requireNonNull(yamlConfiguration, "yamlConfiguration");
        this.quotingChecker = Objects.requireNonNull(quotingChecker, "quotingChecker");
        this.view = view;
        this.readSettings = YamlReadSettings.from(yamlConfiguration);
        this.streamLimits = serdeConfiguration == null
            ? LimitingStream.DEFAULT_LIMITS
            : LimitingStream.limitsFromConfiguration(serdeConfiguration);
        try (Deserializer.DecoderContext context = registry.newDecoderContext(view)) {
            this.coercionPolicy = CoercionPolicy.fromConfiguration(context.getDeserializationConfiguration().orElse(null));
        } catch (IOException e) {
            // the context is only read from here, so completing it cannot fail in practice
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    /**
     * Returns the YAML configuration this mapper writes and reads with.
     *
     * @return The YAML configuration
     */
    public SerdeYamlConfiguration getYamlConfiguration() {
        return yamlConfiguration;
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new YamlObjectMapper(registry, serdeConfiguration, yamlConfiguration, quotingChecker, viewClass);
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
        SerdeConfiguration effective = configuration == null ? serdeConfiguration : configuration;
        SerdeRegistry cloned = introspections == null
            ? registry.cloneWithConfiguration(effective, serializationConfiguration, deserializationConfiguration)
            : registry.cloneWithConfiguration(effective, serializationConfiguration, deserializationConfiguration, introspections);
        return new YamlObjectMapper(cloned, effective, yamlConfiguration, quotingChecker, view);
    }

    /**
     * Creates a copy of this mapper that reads and writes with the given YAML configuration.
     *
     * @param yamlConfiguration The YAML configuration
     * @return A new mapper
     */
    public YamlObjectMapper cloneWithConfiguration(SerdeYamlConfiguration yamlConfiguration) {
        return new YamlObjectMapper(registry, serdeConfiguration, yamlConfiguration, quotingChecker, view);
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return readValue(JsonNodeDecoder.create(tree, streamLimits, coercionPolicy), type);
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        return readValue(new YamlDecoder(inputStream, streamLimits, coercionPolicy, readSettings), type);
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
    }

    private <T> @Nullable T readValue(Decoder decoder, Argument<T> type) throws IOException {
        // A context lives for one document: managed references and object identities do not leak between documents
        try (Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
            Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
            return deserializer.deserializeNullable(decoder, context, type);
        }
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(streamLimits);
        serializeRuntimeTyped(encoder, value);
        return encoder.getCompletedValue();
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(streamLimits);
        serialize(encoder, value, type);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        try (YamlEncoder encoder = newEncoder(outputStream)) {
            if (object == null) {
                encoder.encodeNull();
            } else {
                serializeRuntimeTyped(encoder, object);
            }
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        try (YamlEncoder encoder = newEncoder(outputStream)) {
            if (object == null) {
                encoder.encodeNull();
            } else {
                serialize(encoder, object, type);
            }
        }
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    @Override
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        Objects.requireNonNull(value, "Value to update cannot be null");
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (tree.isNull()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Argument<Object> type = (Argument<Object>) Argument.of(value.getClass());
        updateValue(JsonNodeDecoder.create(tree, streamLimits, coercionPolicy), value, type);
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, InputStream inputStream) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        YamlDecoder decoder = new YamlDecoder(inputStream, streamLimits, coercionPolicy, readSettings);
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (!decoder.decodeNull()) {
            updateValue(decoder, valueToUpdate, type);
        }
        return valueToUpdate;
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, byte[] byteArray) throws IOException {
        Objects.requireNonNull(byteArray, "Byte array cannot be null");
        return updateValue(valueToUpdate, type, new ByteArrayInputStream(byteArray));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void updateValue(Decoder decoder, T value, Argument<T> type) throws IOException {
        try (Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
            Deserializer deserializer = context.findDeserializer(type).createSpecific(context, type);
            if (!(deserializer instanceof UpdatingDeserializer)) {
                deserializer = context.findDeserializer(Argument.OBJECT_ARGUMENT).createSpecific(context, (Argument) type);
            }
            if (!(deserializer instanceof UpdatingDeserializer updatingDeserializer)) {
                throw new UnsupportedOperationException("Updating existing value of type [" + type + "] is not supported");
            }
            updatingDeserializer.deserializeInto(decoder, context, type, value);
        }
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    @SuppressWarnings("unchecked")
    private <T> void serializeRuntimeTyped(Encoder encoder, T object) throws IOException {
        serialize(encoder, object, (Argument<T>) Argument.of(object.getClass()));
    }

    private <T> void serialize(Encoder encoder, T object, Argument<T> type) throws IOException {
        // A context lives for one document: managed references and object identities do not leak between documents
        try (Serializer.EncoderContext context = registry.newEncoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
            Serializer<? super T> serializer = context.findSerializer(type).createSpecific(context, type);
            serializer.serialize(encoder, context, type, object);
        }
    }

    private YamlEncoder newEncoder(OutputStream outputStream) {
        return new YamlEncoder(outputStream, streamLimits, yamlConfiguration, quotingChecker);
    }
}
