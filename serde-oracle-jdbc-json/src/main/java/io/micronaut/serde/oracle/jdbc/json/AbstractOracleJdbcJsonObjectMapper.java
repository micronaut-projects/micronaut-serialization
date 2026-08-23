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
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.BufferingJsonNodeProcessor;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import oracle.sql.json.OracleJsonArray;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonParser;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implementation of the {@link io.micronaut.json.JsonMapper} interface for Oracle JDBC JSON parser/generator.
 *
 * @author Denis Stepanov
 * @since 1.2.0
 */
@Internal
abstract class AbstractOracleJdbcJsonObjectMapper implements ObjectMapper {
    protected final SerdeRegistry registry;
    @Nullable
    protected final SerdeConfiguration serdeConfiguration;
    @Nullable
    protected final Class<?> view;
    protected final OracleJsonFactory oracleJsonFactory = new OracleJsonFactory();
    private final CoercionPolicy coercionPolicy;

    protected AbstractOracleJdbcJsonObjectMapper(SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration) {
        this(registry, serdeConfiguration, null);
    }

    protected AbstractOracleJdbcJsonObjectMapper(SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration, @Nullable Class<?> view) {
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

    abstract OracleJsonParser getJsonParser(InputStream inputStream);

    abstract OracleJsonGenerator createJsonGenerator(OutputStream outputStream);

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        try (var context = registry.newDecoderContext(view)) {
            final Deserializer<? extends T> deserializer = this.registry.findDeserializer(type).createSpecific(context, type);
            return deserializer.deserialize(
                JsonNodeDecoder.create(tree, limits(), coercionPolicy()),
                context,
                type
            );
        }
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        try (OracleJsonParser parser = getJsonParser(inputStream)) {
            return readValue(parser, type);
        }
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        try (OracleJsonParser parser = getJsonParser(new ByteArrayInputStream(byteArray))) {
            return readValue(parser, type);
        }
    }

    /**
     * Read the value using the oracle parser.
     *
     * @param parser The parser
     * @param type   The argument
     * @param <T>    The type
     * @return The value
     * @throws IOException
     */
    public <T> @Nullable T readValue(OracleJsonParser parser, Argument<T> type) throws IOException {
        if (type.getType() == OracleJsonObject.class) {
            OracleJsonParser.Event event = parser.next();
            if (event != OracleJsonParser.Event.START_OBJECT) {
                throw new SerdeException("Invalid state: " + event);
            }
            return (T) parser.getObject();
        }
        if (type.getType() == OracleJsonArray.class) {
            OracleJsonParser.Event event = parser.next();
            if (event != OracleJsonParser.Event.START_ARRAY) {
                throw new SerdeException("Invalid state: " + event);
            }
            return (T) parser.getArray();
        }
        try (var context = registry.newDecoderContext(view)) {
            final Deserializer<? extends T> deserializer = this.registry.findDeserializer(type).createSpecific(context, type);
            return deserializer.deserialize(
                new OracleJdbcJsonParserDecoder(parser, limits(), coercionPolicy()),
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
                try (OracleJsonParser parser = getJsonParser(is)) {
                    final OracleJdbcJsonParserDecoder decoder = new OracleJdbcJsonParserDecoder(parser, limits(), coercionPolicy());
                    final Object o = decoder.decodeArbitrary();
                    return writeValueToTree(o);
                }
            }
        };
    }

    private CoercionPolicy coercionPolicy() {
        return coercionPolicy;
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
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
        try (OracleJsonGenerator generator = createJsonGenerator(Objects.requireNonNull(outputStream, "Output stream cannot be null"))) {
            if (object instanceof OracleJsonObject) {
                generator.write((OracleJsonObject) object);
            } else if (object instanceof OracleJsonArray) {
                generator.write((OracleJsonArray) object);
            } else if (object == null) {
                generator.writeNull();
            } else {
                OracleJdbcJsonGeneratorEncoder encoder = new OracleJdbcJsonGeneratorEncoder(generator, limits());
                serialize(encoder, object);
            }
            generator.flush();
        }
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        try (OracleJsonGenerator generator = createJsonGenerator(Objects.requireNonNull(outputStream, "Output stream cannot be null"))) {
            writeValue(generator, object, type);
        }
    }

    /**
     * Writes the value to the json generator.
     *
     * @param generator The generator
     * @param value     The value
     * @param type      The argument
     * @param <T>       The type
     * @throws IOException
     */
    public <T> void writeValue(OracleJsonGenerator generator, @Nullable T value, Argument<T> type) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else {
            OracleJdbcJsonGeneratorEncoder encoder = new OracleJdbcJsonGeneratorEncoder(generator, limits());
            serialize(encoder, value, type);
        }
        generator.flush();
    }

    private void serialize(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, Argument.of(object.getClass()));
    }

    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        try (var context = registry.newEncoderContext(view)) {
            final Serializer<Object> serializer = registry.findSerializer(type).createSpecific(context, type);
            serializer.serialize(
                encoder,
                context,
                type, object
            );
        }
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
