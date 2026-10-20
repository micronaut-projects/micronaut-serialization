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
package io.micronaut.serde.fory.json;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.BufferingJsonNodeProcessor;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.support.util.JsonViewUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonAccess;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.JsonConfig;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonSharedRegistry;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * {@link ObjectMapper} implementation running Micronaut Serde (de)serializers on top of the
 * Apache Fory JSON reader and writer.
 */
@Internal
@Singleton
@BootstrapContextCompatible
public final class ForyJsonMapper implements ObjectMapper {
    private final SerdeRegistry registry;
    private final @Nullable SerdeConfiguration serdeConfiguration;
    private final @Nullable Class<?> view;
    private final Shared shared;
    private final Serializer.EncoderContext encoderContext;
    private final Deserializer.DecoderContext decoderContext;
    private final @Nullable Argument<?> specificType;
    private final @Nullable Serializer<Object> specificSerializer;
    private final @Nullable Deserializer<Object> specificDeserializer;

    @Inject
    public ForyJsonMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration) {
        this(registry, serdeConfiguration, null, new Shared(), null, null, null);
    }

    private ForyJsonMapper(SerdeRegistry registry,
                           @Nullable SerdeConfiguration serdeConfiguration,
                           @Nullable Class<?> view,
                           Shared shared,
                           @Nullable Argument<?> specificType,
                           @Nullable Serializer<Object> specificSerializer,
                           @Nullable Deserializer<Object> specificDeserializer) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.view = view;
        this.shared = shared;
        this.encoderContext = registry.newEncoderContext(view);
        this.decoderContext = registry.newDecoderContext(view);
        this.specificType = specificType;
        this.specificSerializer = specificSerializer;
        this.specificDeserializer = specificDeserializer;
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public JsonMapper createSpecific(Argument<?> type) {
        @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
        if (viewClass != view) {
            return new ForyJsonMapper(registry, serdeConfiguration, viewClass, shared, null, null, null).createSpecific(type);
        }
        try {
            return new ForyJsonMapper(registry, serdeConfiguration, view, shared, type,
                (Serializer) encoderContext.findSerializer(type).createSpecific(encoderContext, (Argument) type),
                (Deserializer) decoderContext.findDeserializer(type).createSpecific(decoderContext, (Argument) type));
        } catch (Exception e) {
            // unknown type: fall back to the non-specific mapper
            return this;
        }
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
        SerdeRegistry newRegistry = introspections == null
            ? registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration)
            : registry.cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration, introspections);
        return new ForyJsonMapper(newRegistry, configuration == null ? serdeConfiguration : configuration, view, shared, null, null, null);
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new ForyJsonMapper(registry, serdeConfiguration, viewClass, shared, null, null, null);
    }

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext context = decoderContext(type);
        return deserializer(context, type).deserializeNullable(JsonNodeDecoder.create(tree, ForyDecoder.DEFAULT_LIMITS), context, type);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        if (tree.isNull()) {
            return;
        }
        Argument<Object> type = (Argument<Object>) Argument.of(value.getClass());
        Deserializer.DecoderContext context = decoderContext(type);
        Deserializer<?> deserializer = deserializer(context, type);
        if (!(deserializer instanceof UpdatingDeserializer)) {
            deserializer = context.findDeserializer(Argument.OBJECT_ARGUMENT).createSpecific(context, (Argument) type);
        }
        if (!(deserializer instanceof UpdatingDeserializer updatingDeserializer)) {
            throw new UnsupportedOperationException("Updating existing value of type [" + type + "] is not supported");
        }
        updatingDeserializer.deserializeInto(JsonNodeDecoder.create(tree, ForyDecoder.DEFAULT_LIMITS), context, type, value);
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        return readValue(inputStream.readAllBytes(), type);
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return readValue(byteArray, 0, byteArray.length, type);
    }

    @Override
    public <T> @Nullable T readValue(ByteBuffer<?> byteBuffer, Argument<T> type) throws IOException {
        java.nio.ByteBuffer nio = byteBuffer.asNioBuffer();
        if (nio.hasArray()) {
            // read the backing array slice in place
            return readValue(nio.array(), nio.arrayOffset() + nio.position(), nio.remaining(), type);
        }
        return readValue(byteBuffer.toByteArray(), type);
    }

    @Override
    public <T> @Nullable T readValue(ReadBuffer readBuffer, Argument<T> type) throws IOException {
        HeapResult<T> result;
        try {
            result = readBuffer.useFastHeapBuffer(nio -> {
                try {
                    return new HeapResult<>(readValue(nio.array(), nio.arrayOffset() + nio.position(), nio.remaining(), type));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        if (result != null) {
            return result.value();
        }
        // no zero-copy heap view available
        return readValue(readBuffer.toArray(), type);
    }

    private <T> @Nullable T readValue(byte[] bytes, int offset, int length, Argument<T> type) throws IOException {
        Deserializer.DecoderContext context = decoderContext(type);
        Deserializer<? extends T> deserializer = deserializer(context, type);
        State state = shared.acquire();
        Utf8JsonReader reader = state.reader.reset(bytes, offset, length, state.decodeBuffer);
        try {
            T value = deserializer.deserializeNullable(new ForyDecoder(reader), context, type);
            reader.finish();
            return value;
        } catch (ForyJsonException e) {
            throw new JsonSyntaxException(e);
        } finally {
            reader.clear();
            shared.release(state);
        }
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new BufferingJsonNodeProcessor(onSubscribe, streamArray) {
            @Override
            protected JsonNode parseOne(InputStream is) throws IOException {
                return parseOne(is.readAllBytes());
            }

            @Override
            protected JsonNode parseOne(byte[] remaining) throws IOException {
                JsonNode node = readValue(remaining, Argument.of(JsonNode.class));
                return node == null ? JsonNode.nullNode() : node;
            }
        };
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        return writeValueToTree(argumentOf(value), value);
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        if (value == null) {
            return JsonNode.nullNode();
        }
        JsonNodeEncoder encoder = JsonNodeEncoder.create(ForyDecoder.DEFAULT_LIMITS);
        Serializer.EncoderContext context = encoderContext(type);
        serializer(context, type).serialize(encoder, context, type, value);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        outputStream.write(writeValueAsBytes(object));
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        Objects.requireNonNull(outputStream, "Output stream cannot be null");
        outputStream.write(writeValueAsBytes(type, object));
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        if (object == null) {
            return writeValueAsBytes(Argument.OBJECT_ARGUMENT, null);
        }
        return writeValueAsBytes(argumentOf(object), object);
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        State state = shared.acquire();
        Utf8JsonWriter writer = state.writer;
        writer.reset();
        try {
            if (object == null) {
                writer.writeNull();
            } else {
                Serializer.EncoderContext context = encoderContext(type);
                serializer(context, type).serialize(new ForyEncoder(writer), context, type, object);
            }
            return writer.toJsonBytes();
        } finally {
            shared.release(state);
        }
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    @SuppressWarnings("unchecked")
    private static <T> Argument<T> argumentOf(T value) {
        return (Argument<T>) Argument.of(value.getClass());
    }

    private boolean isSpecific(Argument<?> type) {
        return specificType != null && (specificType == type || specificType.equalsType(type));
    }

    private Serializer.EncoderContext encoderContext(Argument<?> type) {
        @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
        return viewClass == view ? encoderContext : registry.newEncoderContext(viewClass);
    }

    private Deserializer.DecoderContext decoderContext(Argument<?> type) {
        @Nullable Class<?> viewClass = JsonViewUtil.extractView(serdeConfiguration, type, view);
        return viewClass == view ? decoderContext : registry.newDecoderContext(viewClass);
    }

    @SuppressWarnings("unchecked")
    private <T> Serializer<? super T> serializer(Serializer.EncoderContext context, Argument<T> type) throws IOException {
        if (specificSerializer != null && context == encoderContext && isSpecific(type)) {
            return specificSerializer;
        }
        return context.findSerializer(type).createSpecific(context, type);
    }

    @SuppressWarnings("unchecked")
    private <T> Deserializer<? extends T> deserializer(Deserializer.DecoderContext context, Argument<T> type) throws IOException {
        if (specificDeserializer != null && context == decoderContext && isSpecific(type)) {
            return (Deserializer<? extends T>) specificDeserializer;
        }
        return context.findDeserializer(type).createSpecific(context, type);
    }

    /**
     * Distinguishes a decoded {@code null} from {@link ReadBuffer#useFastHeapBuffer} not calling the function.
     *
     * @param value The decoded value
     * @param <T> The value type
     */
    private record HeapResult<T>(@Nullable T value) {
    }

    /**
     * Fory reader/writer state shared by all clones of a mapper. One state is cached per thread;
     * re-entrant use (a serializer calling back into the mapper) gets a fresh state.
     */
    private static final class Shared {
        private static final int PROBES = 4;

        private final JsonConfig config;
        private final JsonSharedRegistry sharedRegistry;
        private final AtomicReferenceArray<@Nullable State> slots;
        private final int mask;

        Shared() {
            config = ForyJsonAccess.config(ForyJson.builder().build());
            sharedRegistry = new JsonSharedRegistry(config);
            // bounded by CPUs, not threads: virtual threads must not each retain a reader/writer and buffers
            int size = Integer.highestOneBit(Math.max(1, Runtime.getRuntime().availableProcessors()) * 4 - 1) << 1;
            slots = new AtomicReferenceArray<>(size);
            mask = size - 1;
        }

        private static int home() {
            int h = (int) Thread.currentThread().threadId();
            return h ^ (h >>> 16);
        }

        State acquire() {
            int home = home();
            for (int i = 0; i < PROBES; i++) {
                State state = slots.getAndSet((home + i) & mask, null);
                if (state != null) {
                    return state;
                }
            }
            return new State(config, sharedRegistry);
        }

        void release(State state) {
            int home = home();
            for (int i = 0; i < PROBES; i++) {
                if (slots.compareAndSet((home + i) & mask, null, state)) {
                    return;
                }
            }
            // pool full: drop the state
        }
    }

    private static final class State {
        private static final int INITIAL_BUFFER_SIZE = 8192;

        final Utf8JsonWriter writer;
        final Utf8JsonReader reader;
        final byte[] decodeBuffer = new byte[INITIAL_BUFFER_SIZE];

        State(JsonConfig config, JsonSharedRegistry sharedRegistry) {
            JsonTypeResolver resolver = new JsonTypeResolver(sharedRegistry);
            writer = new Utf8JsonWriter(config, resolver, new byte[INITIAL_BUFFER_SIZE]);
            reader = new Utf8JsonReader(config, resolver);
        }
    }
}
