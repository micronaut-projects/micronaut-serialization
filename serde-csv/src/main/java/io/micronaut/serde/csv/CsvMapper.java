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
package io.micronaut.serde.csv;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * {@link ObjectMapper} implementation for CSV payloads.
 * <p>
 * Deserialization parses CSV input into an intermediate {@index io.micronaut.json.tree.JsonContainer} tree and then delegates
 * value binding to Micronaut Serialization. Serialization first encodes values into a
 * {@link JsonNode} tree and then renders that tree as CSV using {@link SerdeCsvConfiguration}.
 * Header handling for reading and writing is controlled by the CSV-specific configuration.
 *
 * @since 3.1.0
 * @author Hamza Mousrij
 */
@Singleton
@Named(CsvMapper.NAME)
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CsvMapper implements ObjectMapper {

    /**
     * The CSV mapper bean name.
     */
    public static final String NAME = "csv";
    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeCsvConfiguration csvConfiguration;

    /**
     * Creates a CSV mapper.
     *
     * @param serdeRegistry The serde registry
     * @param serdeConfiguration The serde configuration
     * @param csvConfiguration The CSV configuration
     */
    public CsvMapper(SerdeRegistry serdeRegistry,
                     @Nullable SerdeConfiguration serdeConfiguration,
                     @Nullable SerdeCsvConfiguration csvConfiguration) {
        this.registry = serdeRegistry;
        this.serdeConfiguration = serdeConfiguration;
        this.csvConfiguration = csvConfiguration == null ? new SerdeCsvConfiguration() : csvConfiguration;
    }

    /**
     * Returns the serde registry used by this mapper.
     *
     * @return The serde registry
     */
    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    /**
     * Reads a value from an intermediate JSON tree.
     *
     * @param tree The JSON tree
     * @param type The target type
     * @param <T> The target type
     * @return The decoded value
     * @throws IOException If decoding fails
     */
    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
        return deserializer.deserializeNullable(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    /**
     * Reads a CSV value from an input stream.
     *
     * @param inputStream The input stream
     * @param type The target type
     * @param <T> The target type
     * @return The decoded value
     * @throws IOException If reading or decoding fails
     */
    @Override
    public @Nullable <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        return readValue(inputStream.readAllBytes(), type);
    }

    /**
     * Reads a CSV value from bytes.
     *
     * @param byteArray The CSV bytes
     * @param type The target type
     * @param <T> The target type
     * @return The decoded value
     * @throws IOException If decoding fails
     */
    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        JsonNode tree = CsvConverter.parse(new String(byteArray, StandardCharsets.UTF_8), type, csvConfiguration);
        return readValueFromTree(tree, type);
    }

    /**
     * Writes a value to an intermediate JSON tree using runtime type information.
     *
     * @param value The value to encode
     * @return The encoded JSON tree
     * @throws IOException If encoding fails
     */
    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serializeRuntimeTyped(encoder, value);
        return encoder.getCompletedValue();
    }

    /**
     * Writes a value to an intermediate JSON tree using the declared type.
     *
     * @param type The declared type
     * @param value The value to encode
     * @param <T> The value type
     * @return The encoded JSON tree
     * @throws IOException If encoding fails
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
     * Writes a value as CSV to an output stream using runtime type information.
     *
     * @param outputStream The output stream
     * @param object The value to encode
     * @throws IOException If encoding or writing fails
     */
    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        outputStream.write(CsvConverter.write(writeValueToTree(object), csvConfiguration).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a value as CSV to an output stream using the declared type.
     *
     * @param outputStream The output stream
     * @param type The declared type
     * @param object The value to encode
     * @param <T> The value type
     * @throws IOException If encoding or writing fails
     */
    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        outputStream.write(CsvConverter.write(writeValueToTree(type, object), csvConfiguration, inferredHeaders(type)).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a value as CSV bytes using runtime type information.
     *
     * @param object The value to encode
     * @return The CSV bytes
     * @throws IOException If encoding fails
     */
    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    /**
     * Writes a value as CSV bytes using the declared type.
     *
     * @param type The declared type
     * @param object The value to encode
     * @param <T> The value type
     * @return The CSV bytes
     * @throws IOException If encoding fails
     */
    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    /**
     * Returns the stream configuration.
     *
     * @return The stream configuration
     */
    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    private <T> void serialize(Encoder encoder, T object, Argument<T> type) throws IOException {
        Serializer.EncoderContext context = registry.newEncoderContext(null);
        Serializer<? super T> serializer = context.findSerializer(type).createSpecific(context, type);
        serializer.serialize(encoder, context, type, object);
    }

    private @Nullable List<String> inferredHeaders(Argument<?> type) {
        if (csvConfiguration.getWriteHeader() != SerdeCsvConfiguration.Header.FIRST_ROW) {
            return null;
        }
        Argument<?> rowType = rowType(type);
        if (rowType == null || Iterable.class.isAssignableFrom(rowType.getType()) || Map.class.isAssignableFrom(rowType.getType())) {
            return null;
        }
        try {
            return BeanIntrospection.getIntrospection(rowType.getType())
                .getBeanProperties()
                .stream()
                .map(BeanProperty::getName)
                .toList();
        } catch (IntrospectionException e) {
            return null;
        }
    }

    private @Nullable Argument<?> rowType(Argument<?> type) {
        if (Iterable.class.isAssignableFrom(type.getType())) {
            return type.getFirstTypeVariable().orElse(null);
        }
        return type;
    }

    private <T> void serializeRuntimeTyped(Encoder encoder, T object) throws IOException {
        serialize(encoder, object, (Argument<T>) Argument.of(object.getClass()));
    }
}
