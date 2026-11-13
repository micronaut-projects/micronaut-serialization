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
package io.micronaut.serde.csv.apache.commons.decoder;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.apache.commons.CsvRecords;
import io.micronaut.serde.csv.decoder.CsvToMapDecoder;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVFormat;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Default decoder that maps CSV records to maps keyed by header.
 * @since 3.2.5
 */
@Singleton
@Secondary
@Internal
public final class DefaultCsvDecoder extends AbstractCsvDecoder implements CsvToMapDecoder {

    private static final CSVFormat FORMAT = CsvFormats.DEFAULT;

    private Iterable<Map<String, @Nullable String>> decodeAsRecord(CSVFormat format, List<String> columns, String value) {
        return decode(format, () -> new StringReader(value), record -> CsvRecords.toMap(record, columns));
    }

    private Iterable<Map<String, @Nullable String>> decodeAsRecord(CSVFormat format,
                                                         List<String> columns,
                                                         InputStream input,
                                                         Charset charset) {
        return decode(format, () -> new InputStreamReader(input, charset), record -> CsvRecords.toMap(record, columns));
    }

    @Override
    public Iterable<Map<String, @Nullable String>> decode(String value, List<String> header) {
        CSVFormat formatWithHeader = FORMAT.builder()
            .setHeader(header.toArray(new String[0]))
            .setSkipHeaderRecord(false)
            .get();
        return decodeAsRecord(formatWithHeader, List.of(), value);
    }

    public Iterable<Map<String, @Nullable String>> decode(String value) {
        return decodeAsRecord(FORMAT, List.of(), value);
    }

    public Iterable<Map<String, @Nullable String>> decode(InputStream input, Charset charset) {
        return decodeAsRecord(FORMAT, List.of(), input, charset);
    }

    @Override
    public Iterable<Map<String, @Nullable String>> decode(String value, CsvFormatConfig config) {
        CSVFormat format = CsvFormats.from(config);
        List<String> columns = config.hasHeader() ? List.of() : config.columns();
        return decodeAsRecord(format, columns, value);
    }

    @Override
    public Iterable<Map<String, @Nullable String>> decode(InputStream input, Charset charset, CsvFormatConfig config) {
        CSVFormat format = CsvFormats.from(config);
        List<String> columns = config.hasHeader() ? List.of() : config.columns();
        return decodeAsRecord(format, columns, input, charset);
    }
}
