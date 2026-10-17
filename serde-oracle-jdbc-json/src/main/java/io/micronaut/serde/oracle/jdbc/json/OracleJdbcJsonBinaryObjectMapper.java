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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import oracle.sql.json.OracleJsonDatum;
import oracle.sql.json.OracleJsonGenerator;
import oracle.sql.json.OracleJsonParser;
import oracle.sql.json.OracleJsonValue;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of the {@link JsonMapper} interface for Oracle JDBC Binary JSON (OSON).
 * <p>
 * Documents given as bytes are decoded by the native {@link OracleOsonDecoder}, which reads the encoded form in
 * place; {@link #readValue(OracleJsonDatum, Argument)} does the same over the bytes the JDBC driver already
 * holds for a JSON column. The {@link OracleJsonParser} path stays in use for streams, for the
 * {@link OracleJsonValue} tree types and through {@link #readValue(OracleJsonParser, Argument)}.
 *
 * @author Denis Stepanov
 * @since 1.2.0
 */
@Singleton
@BootstrapContextCompatible
@Order(200) // lower precedence than Jackson
public final class OracleJdbcJsonBinaryObjectMapper extends AbstractOracleJdbcJsonObjectMapper {

    private final OracleOsonFieldNameCache fieldNameCache;

    @Internal
    @Inject
    public OracleJdbcJsonBinaryObjectMapper(SerdeRegistry registry, SerdeConfiguration serdeConfiguration) {
        super(registry, serdeConfiguration);
        this.fieldNameCache = new OracleOsonFieldNameCache();
    }

    private OracleJdbcJsonBinaryObjectMapper(SerdeRegistry registry,
                                             @Nullable SerdeConfiguration serdeConfiguration,
                                             @Nullable Class<?> view,
                                             OracleOsonFieldNameCache fieldNameCache) {
        super(registry, serdeConfiguration, view);
        this.fieldNameCache = fieldNameCache;
    }

    @Override
    public JsonMapper cloneWithViewClass(Class<?> viewClass) {
        return new OracleJdbcJsonBinaryObjectMapper(registry, serdeConfiguration, viewClass, fieldNameCache);
    }

    /**
     * Reads an OSON document with the native decoder. The {@link OracleJsonValue} tree types are read with the
     * {@link OracleJsonParser} instead, as the native decoder has no tree.
     *
     * @param byteArray the OSON document
     * @param type      the type to read
     * @param <T>       the type
     * @return the value
     * @throws IOException if the document cannot be decoded
     */
    @Override
    public <T> @Nullable T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        if (OracleJsonValue.class.isAssignableFrom(type.getType())) {
            return super.readValue(byteArray, type);
        }
        return readValue(byteArray, 0, byteArray.length, type);
    }

    /**
     * Reads a JSON column value with the native decoder, straight over the bytes the driver holds; obtain the
     * datum with {@code resultSet.getObject(column, OracleJsonDatum.class)}.
     *
     * @param datum the column value
     * @param type  the type to read
     * @param <T>   the type
     * @return the value
     * @throws IOException if the document cannot be decoded
     * @since 3.3.0
     */
    public <T> @Nullable T readValue(OracleJsonDatum datum, Argument<T> type) throws IOException {
        if (OracleJsonValue.class.isAssignableFrom(type.getType())) {
            return super.readValue(datum.shareBytes(), type);
        }
        byte[] bytes = datum.shareBytes();
        return readValue(bytes, 0, bytes.length, type);
    }

    /**
     * Reads an OSON document held in a slice of an array with the native decoder.
     *
     * @param byteArray the array
     * @param offset    the offset of the document
     * @param length    the length of the document
     * @param type      the type to read
     * @param <T>       the type
     * @return the value
     * @throws IOException if the document cannot be decoded
     * @since 3.3.0
     */
    public <T> @Nullable T readValue(byte[] byteArray, int offset, int length, Argument<T> type) throws IOException {
        try (var context = registry.newDecoderContext(view)) {
            final Deserializer<? extends T> deserializer = registry.findDeserializer(type).createSpecific(context, type);
            return deserializer.deserialize(
                new OracleOsonDecoder(byteArray, offset, length, limits(), coercionPolicy(), fieldNameCache),
                context,
                type
            );
        }
    }

    @Override
    OracleJsonParser getJsonParser(InputStream inputStream) {
        return oracleJsonFactory.createJsonBinaryParser(inputStream);
    }

    @Override
    OracleJsonGenerator createJsonGenerator(OutputStream outputStream) {
        return oracleJsonFactory.createJsonBinaryGenerator(outputStream);
    }
}
