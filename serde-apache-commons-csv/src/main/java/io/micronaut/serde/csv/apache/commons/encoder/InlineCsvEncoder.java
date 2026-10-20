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
package io.micronaut.serde.csv.apache.commons.encoder;

import io.micronaut.core.annotation.Internal;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.encoder.ListToCsvEncoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Encoder for inline-encoded CSV, wrapping each line into a single formatted value.
 * @since 3.2.5
 */
@Singleton
@Secondary
@Internal
public final class InlineCsvEncoder implements ListToCsvEncoder {

    private static final CSVFormat FORMAT = CsvFormats.INLINE;

    private final NoHeaderCsvEncoder noHeaderCsvEncoder;

    /**
     * Creates the inline CSV encoder.
     *
     * @param noHeaderCsvEncoder The encoder used for header-less inline CSV
     */
    public InlineCsvEncoder(NoHeaderCsvEncoder noHeaderCsvEncoder) {
        this.noHeaderCsvEncoder = noHeaderCsvEncoder;
    }

    @Override
    public List<String> encodeLine(@Nullable Object value, CsvFormatConfig config) throws CsvException {
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<@Nullable String> stringList = (List<@Nullable String>) list;
            return noHeaderCsvEncoder.encodeLine(stringList, config);
        }
        throw new CsvException("InlineCsvEncoder only supports List<String> values");
    }

    @Override
    public String encodeToString(Iterable<?> values, CsvFormatConfig config) throws CsvException {
        @SuppressWarnings("unchecked")
        Iterable<List<@Nullable String>> listValues = (Iterable<List<@Nullable String>>) values;
        return encodeToString(listValues);
    }

    @Override
    public String encodeToString(Iterable<?> values, Collection<String> header, CsvFormatConfig config) throws CsvException {
        return encodeToString(values, config);
    }

    public String encodeToString(Iterable<List<@Nullable String>> values) throws CsvException {
        List<String> columns = new ArrayList<>();
        for (List<@Nullable String> it : values) {
            columns.add(encodeToString(it));
        }
        return FORMAT.format((Object[]) columns.toArray(new String[0]));
    }

    public String encodeToString(List<@Nullable String> value) throws CsvException {
        String encoded = noHeaderCsvEncoder.encodeToString(value);
        return encoded.lines().findFirst().orElse("");
    }
}
