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
package io.micronaut.serde.protobuf;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * An {@link ObjectMapper} that reads and writes the Protocol Buffers binary wire format.
 *
 * <p>Unlike the JSON and BSON mappers, this one is not self-describing: a payload can only be read
 * into a concrete type whose properties carry
 * {@link io.micronaut.serde.protobuf.annotation.ProtoField} numbers. Reading into
 * {@link JsonNode} or {@link Object} is therefore not supported.</p>
 *
 * <p>This is a prototype. Its bean is exposed only as {@code ProtobufMapper}, so it cannot be
 * selected as the application's JSON mapper; inject it by its concrete type.</p>
 *
 * @since 3.2
 */
@Experimental
@Singleton
@Bean(typed = ProtobufMapper.class)
public final class ProtobufMapper implements ObjectMapper {

    private final SerdeRegistry registry;
    private final @Nullable SerdeConfiguration serdeConfiguration;
    private final Serializer.EncoderContext encoderContext;
    private final Deserializer.DecoderContext decoderContext;

    /**
     * Default constructor.
     *
     * @param registry           The serde registry
     * @param serdeConfiguration The serde configuration, if one is available
     */
    @Inject
    public ProtobufMapper(SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration) {
        this(registry, serdeConfiguration, null);
    }

    private ProtobufMapper(SerdeRegistry registry,
                           @Nullable SerdeConfiguration serdeConfiguration,
                           @Nullable Class<?> view) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.encoderContext = registry.newEncoderContext(view);
        this.decoderContext = registry.newDecoderContext(view);
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new ProtobufMapper(registry, serdeConfiguration, viewClass);
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        if (object == null) {
            throw new SerdeException("Protocol Buffers cannot represent a null top-level message");
        }
        ProtobufEncoder encoder = new ProtobufEncoder(limits());
        serialize(encoder, type, object);
        return encoder.toByteArray();
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        if (object == null) {
            throw new SerdeException("Protocol Buffers cannot represent a null top-level message");
        }
        return writeValueAsBytes(argumentOf(object), object);
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        outputStream.write(writeValueAsBytes(type, object));
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        outputStream.write(writeValueAsBytes(object));
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        ProtobufDecoder decoder = new ProtobufDecoder(byteArray, limits(), decoderContext);
        return decoderContext.findDeserializer(type)
            .createSpecific(decoderContext, type)
            .deserialize(decoder, decoderContext, type);
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        return readValue(inputStream.readAllBytes(), type);
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, type, value);
        return encoder.getCompletedValue();
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        return value == null ? JsonNode.nullNode() : writeValueToTree(argumentOf(value), value);
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return decoderContext.findDeserializer(type)
            .createSpecific(decoderContext, type)
            .deserialize(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe,
                                                            boolean streamArray) {
        throw new UnsupportedOperationException(
            "Protocol Buffers payloads are not self-delimiting and cannot be parsed into a tree without a schema, "
                + "so reactive parsing is not supported.");
    }

    @SuppressWarnings("unchecked")
    private static <T> Argument<T> argumentOf(T value) {
        return (Argument<T>) Argument.of(value.getClass());
    }

    private <T> void serialize(io.micronaut.serde.Encoder encoder, Argument<T> type, T value) throws IOException {
        Serializer<? super T> serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type);
        serializer.serialize(encoder, encoderContext, type, value);
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null
            ? LimitingStream.DEFAULT_LIMITS
            : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }
}
