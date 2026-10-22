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

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.serde.csv.annotation.CsvFormat;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvShapeException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.serde.csv.mapper.CsvObjectMapper;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

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

    private final CsvMapper csvMapper;

    /**
     * Creates the CSV message body handler.
     *
     * @param csvMapper The CSV mapper used to read and write values
     */
    public CsvMessageHandler(CsvMapper csvMapper) {
        this.csvMapper = csvMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read(Argument<T> type, @Nullable MediaType mediaType, @Nullable Headers httpHeaders, InputStream inputStream) {
        Charset charset = mediaType != null
            ? mediaType.getCharset().orElse(StandardCharsets.UTF_8)
            : StandardCharsets.UTF_8;
        Class<?> rawType = type.getType();
        boolean iterable = Iterable.class.isAssignableFrom(rawType);
        Class<?> elementType = iterable ? type.getWrappedType().getType() : rawType;
        if (elementType == Object.class) {
            elementType = rawType;
        }
        try {
            Iterable<T> values = decode(inputStream, charset, elementType);
            if (iterable) {
                return (T) values;
            }
            CsvFormatConfig config = CsvIntrospection.formatFor(elementType);
            Iterator<T> iterator = values.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalArgumentException("Cannot decode empty CSV to " + rawType.getName());
            }
            T first = iterator.next();
            if (config.shape() != CsvFormat.Shape.FIRST && iterator.hasNext()) {
                throw new CsvShapeException(config.shape(), 2);
            }
            return first;
        } catch (CsvException e) {
            throw new IllegalArgumentException("Failed to decode CSV: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <E> Iterable<T> decode(InputStream inputStream, Charset charset, Class<E> elementType) throws CsvException {
        return (Iterable<T>) csvMapper.read(inputStream, charset, elementType);
    }

    @Override
    public void writeTo(@Nullable Argument<T> type,
                        @Nullable MediaType mediaType,
                        T object,
                        @Nullable MutableHeaders outgoingHeaders,
                        @Nullable OutputStream outputStream) {
        if (outputStream == null) {
            return;
        }
        Charset charset = mediaType != null
            ? mediaType.getCharset().orElse(StandardCharsets.UTF_8)
            : StandardCharsets.UTF_8;
        try {
            if (Iterable.class.isAssignableFrom(type != null ? type.getType() : object.getClass())) {
                csvMapper.writeValues(outputStream, (Iterable<?>) object, charset);
            } else {
                csvMapper.writeValue(outputStream, object, charset);
            }
        } catch (CsvException e) {
            throw new RuntimeException("Failed to encode CSV: " + e.getMessage(), e);
        }
    }

    /**
     * A {@link Produces} annotation for CSV.
     *
     * @since 3.2.5
     */
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
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Consumes({MediaType.TEXT_CSV, MediaType.TEXT_PLAIN})
    public @interface ConsumesCsv {
    }
}
