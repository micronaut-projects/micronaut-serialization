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
package io.micronaut.serde.toon;

import io.micronaut.context.annotation.Secondary;
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
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.toon.util.ToonDecoder;
import io.micronaut.serde.toon.util.ToonEncoder;
import jakarta.inject.Inject;
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
 * A TOON (Token-Oriented Object Notation)-backed {@link ObjectMapper}.
 *
 * <p>Reads a TOON document into a {@link JsonNode} tree and deserializes
 * from that tree. Serializes to a {@link JsonNode} tree and writes it as a
 * TOON document.</p>
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Singleton
@Secondary
@Named(ToonMapper.NAME)
public final class ToonMapper implements ObjectMapper {

    /**
     * The qualifier name of the TOON {@link ObjectMapper} bean.
     */
    public static final String NAME = "toon";

    private final SerdeRegistry registry;

    @Nullable
    private final SerdeConfiguration serdeConfiguration;

    private final ToonDecoder toonDecoder;

    private final ToonEncoder toonEncoder;

    /**
     * Creates a TOON-backed {@link ObjectMapper}.
     *
     * @param registry           The serde registry used to resolve serializers and deserializers
     * @param serdeConfiguration The serde configuration, when available
     * @param toonDecoder        The decoder that converts TOON input into a JSON tree
     * @param toonEncoder        The encoder that writes JSON trees as TOON output
     */
    @Inject
    public ToonMapper(SerdeRegistry registry,
                      @Nullable SerdeConfiguration serdeConfiguration,
                      ToonDecoder toonDecoder,
                      ToonEncoder toonEncoder) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.toonDecoder = toonDecoder;
        this.toonEncoder = toonEncoder;
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
     * @param <T>  Type variable of the return type
     * @return The deserialized value
     * @throws IOException If tree decoding fails
     */
    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        try (var decoderContext = registry.newDecoderContext(null)) {
            Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext, type);
            return deserializer.deserializeNullable(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
        }
    }

    /**
     * Parse and map TOON data from the given stream.
     *
     * @param inputStream The TOON input stream
     * @param type        The target type
     * @param <T>         Type variable of the return type
     * @return The deserialized object
     * @throws IOException If the TOON input cannot be read
     */
    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        JsonNode tree = toonDecoder.parse(inputStream, limits());
        return readValueFromTree(tree, type);
    }

    /**
     * Parse and map TOON data from the given byte array.
     *
     * @param byteArray The TOON bytes
     * @param type      The target type
     * @param <T>       Type variable of the return type
     * @return The deserialized object
     * @throws IOException If the TOON input cannot be read
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
     * @param type  The declared type of the value
     * @param value The value to convert
     * @param <T>   The type variable of the type
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
     * Write an object as a TOON document.
     *
     * @param outputStream The destination stream
     * @param object       The object to serialize
     * @throws IOException If writing the TOON output fails
     */
    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        toonEncoder.write(outputStream, writeValueToTree(object));
    }

    /**
     * Write an object as a TOON document.
     *
     * @param outputStream The destination stream
     * @param type         The declared type of the object
     * @param object       The object to serialize
     * @param <T>          The generic type
     * @throws IOException If writing the TOON output fails
     */
    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        toonEncoder.write(outputStream, writeValueToTree(type, object));
    }

    /**
     * Write an object as a TOON document.
     *
     * @param object The object to serialize
     * @return The serialized TOON bytes
     * @throws IOException If writing the TOON output fails
     */
    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeValue(output, object);
            return output.toByteArray();
        }
    }

    /**
     * Write an object as a TOON document.
     *
     * @param type   The declared type of the object
     * @param object The object to serialize
     * @param <T>    The generic type
     * @return The serialized TOON bytes
     * @throws IOException If writing the TOON output fails
     */
    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeValue(output, type, object);
            return output.toByteArray();
        }
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
        try (var context = registry.newEncoderContext(null)) {
            Serializer<? super T> serializer = context.findSerializer(type).createSpecific(context, type);
            serializer.serialize(encoder, context, type, object);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void serializeRuntimeTyped(Encoder encoder, T object) throws IOException {
        serialize(encoder, object, (Argument<T>) Argument.of(object.getClass()));
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }
}
