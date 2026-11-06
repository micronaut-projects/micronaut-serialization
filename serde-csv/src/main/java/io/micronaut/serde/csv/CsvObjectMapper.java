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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvRecordMappings;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.support.util.JsonViewUtil;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Internal
@SuppressWarnings("checkstyle:DesignForExtension")
public abstract class CsvObjectMapper implements ObjectMapper {

    protected final SerdeRegistry registry;
    @Nullable
    protected final SerdeConfiguration serdeConfiguration;
    @Nullable
    protected final Class<?> view;
    protected final LimitingStream.RemainingLimits streamLimits;
    protected final CoercionPolicy coercionPolicy;
    private final ConcurrentHashMap<Class<?>, Serializer<Object>> serializerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Deserializer<Object>> deserializerCache = new ConcurrentHashMap<>();

    protected CsvObjectMapper(SerdeRegistry registry,
                            @Nullable SerdeConfiguration serdeConfiguration,
                            @Nullable Class<?> view) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.serdeConfiguration = serdeConfiguration;
        this.view = view;
        this.streamLimits = serdeConfiguration == null
            ? LimitingStream.DEFAULT_LIMITS
            : LimitingStream.limitsFromConfiguration(serdeConfiguration);
        try (Deserializer.DecoderContext context = registry.newDecoderContext(view)) {
            this.coercionPolicy = CoercionPolicy.fromConfiguration(context.getDeserializationConfiguration().orElse(null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return registry;
    }

    @Override
    public ObjectMapper cloneWithViewClass(Class<?> viewClass) {
        return cloneWithConfiguration(serdeConfiguration, null, null, null);
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
        return newCsvObjectMapper(cloned, effective, view);
    }

    protected abstract CsvObjectMapper newCsvObjectMapper(SerdeRegistry registry,
                                                          @Nullable SerdeConfiguration configuration,
                                                          @Nullable Class<?> view);

    protected abstract Encoder newEncoder(OutputStream outputStream);

    protected abstract Decoder newDecoder(InputStream inputStream);

    /**
     * Encodes logical cell values using the backend CSV library.
     *
     * @param format The resolved CSV format
     * @param cells  The logical cell values
     * @return The encoded record without a trailing line separator
     * @throws IOException If the record cannot be encoded
     */
    protected abstract String encodeCsvLine(CsvFormatConfig format, List<@Nullable String> cells) throws IOException;

    /**
     * Decodes one record using the backend CSV library.
     *
     * @param format The resolved CSV format
     * @param line   The encoded record
     * @return The logical cell values
     * @throws IOException If the record cannot be decoded
     */
    protected abstract List<@Nullable String> decodeCsvLine(CsvFormatConfig format, String line) throws IOException;

    @Override
    public <T> @Nullable T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return readValue(JsonNodeDecoder.create(tree, streamLimits, coercionPolicy), type);
    }

    @Override
    public <T> @Nullable T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        Decoder decoder = newDecoder(inputStream);
        return readValue(decoder.decodeObject(type), type);
    }

    private <T> @Nullable T readValue(Decoder decoder, Argument<T> type) throws IOException {
        return deserializeRow(decoder, type);
    }

    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
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
        if (object == null) {
            return;
        }
        writeValuesWithHeader(outputStream, List.of(object), null);
    }

    public void writeValues(OutputStream outputStream, Iterable<?> values) throws IOException {
        writeValuesWithHeader(outputStream, values, null);
    }

    private void writeValuesWithHeader(OutputStream outputStream,
                             Iterable<?> values,
                             @Nullable Collection<String> header) throws IOException {
        Iterator<?> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return;
        }
        Object first = iterator.next();
        requireWritable(first);
        CsvFormatConfig format = CsvIntrospection.formatFor(first.getClass());
        try (Encoder encoder = newEncoder(outputStream)) {
            if (encoder instanceof CsvEncoder csvEncoder) {
                csvEncoder.configure(format);
                if (header != null && !header.isEmpty()) {
                    csvEncoder.writeHeader(new ArrayList<>(header));
                } else if (format.writeHeader() && !format.columns().isEmpty()) {
                    csvEncoder.writeHeader(format.columns());
                }
            }
            serializeRuntimeTyped(encoder, first);
            while (iterator.hasNext()) {
                Object value = iterator.next();
                requireWritable(value);
                serializeRuntimeTyped(encoder, value);
            }
        }
    }

    private static void requireWritable(@Nullable Object value) throws IOException {
        if (value == null) {
            throw new io.micronaut.serde.csv.error.CsvException("Cannot write null record");
        }
        if (value instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (key == null) {
                    throw new io.micronaut.serde.csv.error.CsvException("Cannot write null map key");
                }
            }
        }
    }

    public <T> List<T> readValues(InputStream inputStream, Argument<T> elementType) throws IOException {
        List<T> result = new ArrayList<>();
        try (CsvDecoder decoder = (CsvDecoder) newDecoder(inputStream)) {
            while (decoder.hasNextRow(elementType)) {
                T value = deserializeRow(decoder.decodeObject(elementType), elementType);
                if (value == null) {
                    break;
                }
                result.add(value);
            }
        }
        return result;
    }

    // Convenience methods mirroring the legacy CsvMapper API for test compatibility
    public <T> Iterable<T> readAll(String value, Class<T> type) throws IOException {
        return new LazyCsvList<>(value.getBytes(StandardCharsets.UTF_8), Argument.of(type));
    }

    public <T> Iterable<T> read(InputStream input, Charset charset, Class<T> type) throws IOException {
        return () -> new CsvRowIterator<>((CsvDecoder) newDecoder(input), Argument.of(type));
    }

    public <T> Iterable<T> read(InputStream input, String charsetName, Class<T> type) throws IOException {
        return read(input, Charset.forName(charsetName), type);
    }

    public <T> @Nullable T read(List<String> value, Class<T> type) throws IOException {
        CsvFormatConfig format = CsvIntrospection.formatFor(type);
        Map<String, @Nullable String> row = CsvRecordMappings.rowToPropertyMap(format, value);
        Decoder rowDecoder = CsvDecoder.forRow(row).decodeObject(Argument.of(type));
        return deserializeRow(rowDecoder, Argument.of(type));
    }

    public <T> Iterable<T> read(Iterable<List<String>> rows, Class<T> type) throws IOException {
        return () -> new Iterator<>() {
            private final Iterator<List<String>> delegate = rows.iterator();

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
                try {
                    T result = read(delegate.next(), type);
                    if (result == null) {
                        throw new NoSuchElementException();
                    }
                    return result;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    public String writeToString(Iterable<?> values) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeValues(baos, values);
        return baos.toString(StandardCharsets.UTF_8);
    }

    public String writeToString(Iterable<?> values, Collection<String> header) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeValuesWithHeader(baos, values, header);
        return baos.toString(StandardCharsets.UTF_8);
    }

    public List<@Nullable String> writeAsLine(@Nullable Object value) throws IOException {
        if (value == null) {
            return List.of();
        }
        CsvFormatConfig format = CsvIntrospection.formatFor(value.getClass());
        try (Encoder encoder = newEncoder(new ByteArrayOutputStream())) {
            if (encoder instanceof CsvEncoder csvEncoder) {
                csvEncoder.configure(format);
            }
            serializeRuntimeTyped(encoder, value);
            return encoder instanceof CsvEncoder csvEncoder ? csvEncoder.lastRow() : List.of();
        }
    }

    public void writeValue(OutputStream outputStream, Object value, Charset charset) throws IOException {
        writeValue(outputStream, value);
    }

    public void writeValues(OutputStream outputStream, Iterable<?> values, Charset charset) throws IOException {
        writeValues(outputStream, values);
    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {
        if (object == null) {
            return;
        }
        CsvFormatConfig format = CsvIntrospection.formatFor(type.getType());
        try (Encoder encoder = newEncoder(outputStream)) {
            if (encoder instanceof CsvEncoder csvEncoder) {
                csvEncoder.configure(format);
                if (format.writeHeader() && !format.columns().isEmpty()) {
                    csvEncoder.writeHeader(format.columns());
                }
            }
            serialize(encoder, object, type);
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
        throw new UnsupportedOperationException("CSV does not support updating from a JSON tree");
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, InputStream inputStream) throws IOException {
        throw new UnsupportedOperationException("CSV does not support updating existing values");
    }

    @Override
    public <T> T updateValue(T valueToUpdate, Argument<T> type, byte[] byteArray) throws IOException {
        throw new UnsupportedOperationException("CSV does not support updating existing values");
    }

    @Override
    public io.micronaut.json.JsonStreamConfig getStreamConfig() {
        return io.micronaut.json.JsonStreamConfig.DEFAULT;
    }

    private <T> void serialize(Encoder encoder, T object, Argument<T> type) throws IOException {
        Class<?> clazz = type.getType();
        if (clazz != Map.class && clazz != java.util.LinkedHashMap.class
                && clazz != java.util.HashMap.class
                && io.micronaut.core.beans.BeanIntrospector.SHARED.findIntrospection(clazz).isPresent()) {
            Serializer<Object> serializer = serializerCache.computeIfAbsent(clazz, c -> new CsvBeanSerializer(c, this::encodeCsvLine));
            try (Serializer.EncoderContext context = registry.newEncoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
                serializer.serialize(encoder, context, type, object);
            }
            return;
        }
        try (Serializer.EncoderContext context = registry.newEncoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
            Serializer<? super T> serializer = context.findSerializer(type).createSpecific(context, type);
            serializer.serialize(encoder, context, type, object);
        }
    }

    @SuppressWarnings("unchecked")
    private void serializeRuntimeTyped(Encoder encoder, Object object) throws IOException {
        serialize(encoder, object, (Argument<Object>) Argument.of(object.getClass()));
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T deserializeRow(Decoder rowDecoder, Argument<T> type) throws IOException {
        Class<?> clazz = type.getType();
        if (clazz != Map.class && clazz != java.util.LinkedHashMap.class
                && clazz != java.util.HashMap.class
                && io.micronaut.core.beans.BeanIntrospector.SHARED.findIntrospection(clazz).isPresent()) {
            Deserializer<Object> deserializer = deserializerCache.computeIfAbsent(clazz, c -> {
                Deserializer.DecoderContext ctx = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
                return new CsvBeanDeserializer(c, this::decodeCsvLine, ctx);
            });
            try (Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
                @SuppressWarnings("unchecked")
                Argument<? super Object> arg = (Argument<? super Object>) (Object) type;
                return (T) deserializer.deserializeNullable(rowDecoder, context, arg);
            }
        }
        if (isRawMap(type) && rowDecoder instanceof CsvDecoder csvDecoder) {
            return (T) new LinkedHashMap<>(csvDecoder.row());
        }
        try (Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view))) {
            Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
            return deserializer.deserializeNullable(rowDecoder, context, type);
        }
    }

    private static boolean isRawMap(Argument<?> type) {
        Class<?> clazz = type.getType();
        return Map.class.isAssignableFrom(clazz);
    }

    private final class LazyCsvList<T> extends AbstractList<T> {
        private final byte[] bytes;
        private final Argument<T> type;
        private final List<T> cache = new ArrayList<>();
        @Nullable
        private CsvDecoder decoder;
        private boolean exhausted;

        LazyCsvList(byte[] bytes, Argument<T> type) {
            this.bytes = bytes;
            this.type = type;
        }

        private void fill(int index) throws IOException {
            if (decoder == null) {
                decoder = (CsvDecoder) newDecoder(new ByteArrayInputStream(bytes));
            }
            while (!exhausted && cache.size() <= index) {
                if (decoder.hasNextRow(type)) {
                    cache.add(deserializeRow(decoder.decodeObject(type), type));
                } else {
                    exhausted = true;
                    decoder.close();
                }
            }
        }

        @Override
        public T get(int index) {
            try {
                fill(index);
                return cache.get(index);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public int size() {
            try {
                fill(Integer.MAX_VALUE);
                return cache.size();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private final class CsvRowIterator<T> implements Iterator<T> {
        private final CsvDecoder decoder;
        private final Argument<T> type;
        private boolean prepared;
        private boolean available;

        CsvRowIterator(CsvDecoder decoder, Argument<T> type) {
            this.decoder = decoder;
            this.type = type;
        }

        @Override
        public boolean hasNext() {
            if (!prepared) {
                try {
                    available = decoder.hasNextRow(type);
                    prepared = true;
                    if (!available) {
                        decoder.close();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return available;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            prepared = false;
            try {
                T result = deserializeRow(decoder.decodeObject(type), type);
                if (result == null) {
                    throw new NoSuchElementException();
                }
                return result;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
