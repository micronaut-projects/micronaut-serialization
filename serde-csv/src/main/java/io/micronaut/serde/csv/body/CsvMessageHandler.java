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
package io.micronaut.serde.csv.body;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.serde.csv.CsvObjectMapper;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * {@link MessageBodyHandler} that encodes and decodes CSV using the {@link CsvObjectMapper}.
 *
 * @param <T> The handled type
 * @since 3.2.5
 */
@Singleton
@CsvMessageHandler.ProducesCsv
@CsvMessageHandler.ConsumesCsv
@BootstrapContextCompatible
public final class CsvMessageHandler<T> implements MessageBodyHandler<T> {

    private final CsvObjectMapper csvObjectMapper;

    /**
     * Creates the CSV message body handler.
     *
     * @param csvObjectMapper The CSV object mapper used to read and write values
     */
    public CsvMessageHandler(CsvObjectMapper csvObjectMapper) {
        this.csvObjectMapper = csvObjectMapper;
    }

    @Override
    public boolean isReadable(Argument<T> type, @Nullable MediaType mediaType) {
        Class<?> rawType = type.getType();
        return !Iterable.class.isAssignableFrom(rawType) || isSupportedCollection(rawType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read(Argument<T> type, @Nullable MediaType mediaType, @Nullable Headers httpHeaders, InputStream inputStream) {
        Class<?> rawType = type.getType();
        boolean iterable = Iterable.class.isAssignableFrom(rawType);
        Class<?> elementType = iterable ? type.getWrappedType().getType() : rawType;
        try {
            if (iterable) {
                return readIterable(type, elementType, inputStream);
            }
            T first = csvObjectMapper.readValue(inputStream, Argument.of((Class<T>) elementType));
            if (first == null) {
                throw new IllegalArgumentException("Cannot decode empty CSV to " + rawType.getName());
            }
            return first;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode CSV: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E> T readIterable(Argument<T> type, Class<?> elementType, InputStream inputStream) throws IOException {
        return (T) materialize(csvObjectMapper.readValues(inputStream, Argument.of((Class<E>) elementType)), type.getType());
    }

    /**
     * Adapts the backend's lazy {@link Iterable} to the requested collection type. A raw
     * {@link Iterable} request keeps the lazy result, while {@link java.util.List} and
     * {@link java.util.Set} requests are materialized so the returned value is assignable to the
     * declared type.
     *
     * @param values  The decoded values
     * @param rawType The requested raw type
     * @param <V>     The element type
     * @return A value assignable to the requested type
     */
    private static <V> Iterable<V> materialize(Iterable<V> values, Class<?> rawType) {
        if (!isSupportedCollection(rawType)) {
            throw new IllegalArgumentException("Cannot decode CSV to " + rawType.getName()
                + "; supported collection types are Iterable, Collection, List and Set");
        }
        if (rawType == Iterable.class) {
            return values;
        }
        if (rawType.isAssignableFrom(ArrayList.class)) {
            ArrayList<V> result = new ArrayList<>();
            values.forEach(result::add);
            return result;
        }
        LinkedHashSet<V> result = new LinkedHashSet<>();
        values.forEach(result::add);
        return result;
    }

    private static boolean isSupportedCollection(Class<?> rawType) {
        return rawType == Iterable.class
            || rawType.isAssignableFrom(ArrayList.class)
            || rawType.isAssignableFrom(LinkedHashSet.class);
    }

    @Override
    public void writeTo(@Nullable Argument<T> type,
                        @Nullable MediaType mediaType,
                        @Nullable T object,
                        @Nullable MutableHeaders outgoingHeaders,
                        @Nullable OutputStream outputStream) {
        if (outputStream == null || object == null) {
            return;
        }
        try {
            if (Iterable.class.isAssignableFrom(type != null ? type.getType() : object.getClass())) {
                csvObjectMapper.writeValues(outputStream, (Iterable<?>) object);
            } else {
                csvObjectMapper.writeValue(outputStream, object);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode CSV: " + e.getMessage(), e);
        }
    }

    /**
     * A {@link Produces} annotation for CSV.
     *
     * @since 3.2.5
     */
    @Internal
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Produces({MediaType.TEXT_CSV, MediaType.TEXT_PLAIN})
    public @interface ProducesCsv {
    }

    /**
     * A {@link Consumes} annotation for CSV.
     *
     * @since 3.2.5
     */
    @Internal
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Consumes({MediaType.TEXT_CSV, MediaType.TEXT_PLAIN})
    public @interface ConsumesCsv {
    }

}
