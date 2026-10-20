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
package io.micronaut.serde.csv.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.mapper.CsvObjectMapper;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Serde that serializes an {@link Iterable} as a single CSV string.
 * @since 3.2.5
 */
@Singleton
@Secondary
public final class CsvSerde implements Serde<Iterable<?>> {

    private final CsvObjectMapper csvMapper;

    /**
     * Creates the CSV serde.
     *
     * @param csvMapper The CSV mapper used to read and write values
     */
    public CsvSerde(CsvObjectMapper csvMapper) {
        this.csvMapper = csvMapper;
    }

    @Override
    public void serialize(Encoder encoder,
                          Serializer.EncoderContext context,
                          Argument<? extends Iterable<?>> type,
                          Iterable<?> value) throws IOException {
        try {
            String encoded = csvMapper.writeToString(value);
            encoder.encodeString(encoded);
        } catch (CsvException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Iterable<?> deserialize(Decoder decoder,
                                    Deserializer.DecoderContext context,
                                    Argument<? super Iterable<?>> type) throws IOException {
        try {
            Class<?> elementType = type.getTypeParameters()[0].getType();
            return csvMapper.readAll(decoder.decodeString(), elementType);
        } catch (CsvException e) {
            throw new IOException(e);
        }
    }
}
