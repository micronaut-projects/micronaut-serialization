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
package io.micronaut.serde.csv.apache.commons.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.csv.apache.commons.decoder.InlineCsvDecoder;
import io.micronaut.serde.csv.apache.commons.encoder.InlineCsvEncoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.mapper.CsvObjectMapper;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serde that serializes an {@link Iterable} as a single inline-encoded CSV string.
 *
 * @since 3.2.5
 */
@Singleton
@Secondary
public final class InlineCsvSerde implements Serde<Iterable<?>> {

    private final InlineCsvEncoder inlineCsvEncoder;
    private final InlineCsvDecoder inlineCsvDecoder;
    private final CsvObjectMapper csvMapper;

    /**
     * Creates the inline CSV serde.
     *
     * @param inlineCsvEncoder The inline CSV encoder
     * @param inlineCsvDecoder The inline CSV decoder
     * @param csvMapper        The CSV object mapper
     */
    public InlineCsvSerde(InlineCsvEncoder inlineCsvEncoder,
                          InlineCsvDecoder inlineCsvDecoder,
                          CsvObjectMapper csvMapper) {
        this.inlineCsvEncoder = inlineCsvEncoder;
        this.inlineCsvDecoder = inlineCsvDecoder;
        this.csvMapper = csvMapper;
    }

    @Override
    public void serialize(Encoder encoder,
                          Serializer.EncoderContext context,
                          Argument<? extends Iterable<?>> type,
                          Iterable<?> value) throws IOException {
        try {
            List<List<String>> values = new ArrayList<>();
            for (Object it : value) {
                if (it == null) {
                    values.add(List.of());
                    continue;
                }
                values.add(csvMapper.writeAsLine(it));
            }
            String encoded = inlineCsvEncoder.encodeToString(values);
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
            String input = decoder.decodeString();
            Iterable<List<String>> values = inlineCsvDecoder.decode(input, CsvFormatConfig.DEFAULT);
            Class<?> wrappedClass = type.getTypeParameters()[0].getType();
            return csvMapper.read((Iterable<List<String>>) values, wrappedClass);
        } catch (CsvException e) {
            throw new IOException(e);
        }
    }
}
