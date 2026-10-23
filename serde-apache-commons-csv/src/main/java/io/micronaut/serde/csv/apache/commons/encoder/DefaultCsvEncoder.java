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

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.encoder.MapToCsvEncoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Default encoder that maps records to CSV strings with an optional header.
 * @since 3.2.5
 */
@Singleton
@Secondary
@Internal
public final class DefaultCsvEncoder extends AbstractCsvEncoder implements MapToCsvEncoder {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setRecordSeparator('\n')
        .get();

    private String encodeRawLinesToString(CSVFormat encodingFormat,
                                           String @Nullable [] projectionHeader,
                                           Iterable<Map<String, @Nullable String>> values) throws CsvException {
        // Project each record onto its columns lazily so encoding large inputs does not
        // materialize every row in memory before it is written.
        Iterable<List<@Nullable String>> rows = () -> new Iterator<>() {
            private final Iterator<Map<String, @Nullable String>> records = values.iterator();

            @Override
            public boolean hasNext() {
                return records.hasNext();
            }

            @Override
            public List<@Nullable String> next() {
                Map<String, @Nullable String> value = records.next();
                if (projectionHeader == null) {
                    return new ArrayList<>(value.values());
                }
                List<@Nullable String> row = new ArrayList<>(projectionHeader.length);
                for (String column : projectionHeader) {
                    row.add(value.get(column));
                }
                return row;
            }
        };
        return encodeToString(encodingFormat, rows);
    }

    @Override
    public List<String> encodeLine(@Nullable Object value, CsvFormatConfig config) {
        throw new UnsupportedOperationException("encodeLine not supported for MapToCsvEncoder");
    }

    @Override
    public String encodeToString(Iterable<?> values, CsvFormatConfig config) throws CsvException {
        @SuppressWarnings("unchecked")
        Iterable<Map<String, @Nullable String>> mapValues = (Iterable<Map<String, @Nullable String>>) values;
        boolean withHeader = config.writeHeader() && !config.columns().isEmpty();
        String[] projectionHeader = config.columns().isEmpty() ? null : config.columns().toArray(new String[0]);
        return encodeRawLinesToString(CsvFormats.format(config, withHeader), projectionHeader, mapValues);
    }

    @Override
    public String encodeToString(Iterable<?> values, Collection<String> header, CsvFormatConfig config) throws CsvException {
        @SuppressWarnings("unchecked")
        Iterable<Map<String, @Nullable String>> mapValues = (Iterable<Map<String, @Nullable String>>) values;
        return encodeRawLinesToString(CsvFormats.format(config, true), header.toArray(new String[0]), mapValues);
    }

    @Override
    public String encodeToString(Map<String, @Nullable String> value, Collection<String> columns) throws CsvException {
        return encodeToString(List.of(value), columns);
    }

    @Override
    public String encodeToString(Iterable<Map<String, @Nullable String>> values, Collection<String> columns) throws CsvException {
        return encodeRawLinesToString(FORMAT, columns.toArray(new String[0]), values);
    }
}
