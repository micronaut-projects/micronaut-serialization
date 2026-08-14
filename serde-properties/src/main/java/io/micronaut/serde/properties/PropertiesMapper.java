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
package io.micronaut.serde.properties;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.json.stream.JsonStreamMapper;
import io.micronaut.serde.properties.util.PropertiesTreeAdapter;
import io.micronaut.serde.properties.util.PropertiesWriter;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A Java {@code .properties}-backed {@link ObjectMapper}.
 *
 * <p>Reads and parses flat {@code .properties} documents into an intermediate
 * {@link JsonNode} tree before delegating to Micronaut Serialization.</p>
 *
 * <p>Serializes values to a {@link JsonNode} tree first, then flattens
 * object paths and array indexes into {@code .properties} key/value lines.
 * Array index syntax is controlled by {@link SerdePropertiesConfiguration};
 * the default is bracketed indexes.</p>
 *
 * @author Mousrij Hamza
 * @since 3.1.0
 */
@Singleton
@Secondary
@Named(PropertiesMapper.NAME)
public final class PropertiesMapper implements ObjectMapper {

    /**
     * The qualifier name of the {@code .properties} {@link ObjectMapper} bean.
     */
    public static final String NAME = "properties";
    private static final Argument<JsonNode> JSON_NODE_TYPE = Argument.of(JsonNode.class);
    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    @NonNull
    private final PropertiesTreeAdapter propertiesTreeAdapter;
    @NonNull
    private final PropertiesWriter propertiesWriter;
    private final JsonStreamMapper jsonStreamMapper;

    /**
     * Creates a Java {@code .properties}-backed {@link ObjectMapper}.
     *
     * @param registry The serde registry used to resolve serializers and deserializers
     * @param serdeConfiguration The serde configuration, when available
     * @param propertiesTreeAdapter The adapter that converts properties input into a JSON tree
     * @param propertiesWriter The writer that flattens JSON trees into properties output
     * @param jsonStreamMapper The JSON stream mapper used for intermediary tree conversion
     */
    @Inject
    public PropertiesMapper(SerdeRegistry registry,
                            @Nullable SerdeConfiguration serdeConfiguration,
                            PropertiesTreeAdapter propertiesTreeAdapter,
                            PropertiesWriter propertiesWriter,
                            JsonStreamMapper jsonStreamMapper) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.propertiesTreeAdapter = propertiesTreeAdapter;
        this.propertiesWriter = propertiesWriter;
        this.jsonStreamMapper = jsonStreamMapper;
    }

    /**
     * Returns the {@link SerdeRegistry} used by this object mapper, if possible.
     *
     * @return The serde registry
     */
    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    /**
     * Transform a {@link JsonNode} to a value of the given type.
     *
     * @param tree The source tree to deserialize
     * @param type The target type
     * @param <T> Type variable of the return type
     * @return The deserialized value
     * @throws IOException If tree decoding fails
     */
    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        return deserializer.deserializeNullable(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    /**
     * Parse and map {@code .properties} data from the given stream.
     *
     * @param inputStream The properties input stream
     * @param type The target type
     * @param <T> Type variable of the return type
     * @return The deserialized object
     * @throws IOException If the properties input cannot be read
     */
    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        JsonNode tree = propertiesTreeAdapter.parse(inputStream);
        byte[] json = jsonStreamMapper.writeValueAsBytes(JSON_NODE_TYPE, tree);
        return jsonStreamMapper.readValue(json, type);
    }

    /**
     * Parse and map {@code .properties} data from the given byte array.
     *
     * @param byteArray The properties bytes
     * @param type The target type
     * @param <T> Type variable of the return type
     * @return The deserialized object
     * @throws IOException If the properties input cannot be read
     */
    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
    }

    /**
     * Transform an object value to a JSON tree.
     *
     * @param value The value to convert
     * @return The JSON representation
     * @throws IOException If serialization to the tree fails
     */
    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serializeRuntimeTyped(encoder, value);
        return encoder.getCompletedValue();
    }

    /**
     * Transform an object value to a JSON tree.
     *
     * @param type The declared type of the value
     * @param value The value to convert
     * @param <T> The type variable of the type
     * @return The JSON representation
     * @throws IOException If serialization to the tree fails
     */
    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }

        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value, type);
        return encoder.getCompletedValue();
    }

    /**
     * Write an object as {@code .properties} data.
     *
     * @param outputStream The destination stream
     * @param object The object to serialize
     * @throws IOException If writing the properties output fails
     */
    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        propertiesWriter.write(outputStream, writeValueToTree(object));
    }

    /**
     * Write an object as {@code .properties} data.
     *
     * @param outputStream The destination stream
     * @param type The declared type of the object
     * @param object The object to serialize
     * @param <T> The generic type
     * @throws IOException If writing the properties output fails
     */
    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        propertiesWriter.write(outputStream, writeValueToTree(type, object));
    }

    /**
     * Write an object as {@code .properties} data.
     *
     * @param object The object to serialize
     * @return The serialized {@code .properties} bytes
     * @throws IOException If writing the properties output fails
     */
    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    /**
     * Write an object as {@code .properties} data.
     *
     * @param type The declared type of the object
     * @param object The object to serialize
     * @param <T> The generic type
     * @return The serialized {@code .properties} bytes
     * @throws IOException If writing the properties output fails
     */
    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    /**
     * Returns the stream configuration used by this mapper.
     *
     * @return The configured stream config
     */
    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    private <T> void serialize(Encoder encoder, T object, Argument<T> type) throws IOException {
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        Serializer<? super T> serializer = context.findSerializer(type).createSpecific(context, type);
        serializer.serialize(encoder, context, type, object);
    }

    @SuppressWarnings("unchecked")
    private <T> void serializeRuntimeTyped(Encoder encoder, T object) throws IOException {
        serialize(encoder, object, (Argument<T>) Argument.of(object.getClass()));
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }
}
