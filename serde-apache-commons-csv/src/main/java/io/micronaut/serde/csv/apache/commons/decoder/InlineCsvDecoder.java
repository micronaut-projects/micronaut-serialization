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

import io.micronaut.core.annotation.Internal;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.serde.csv.apache.commons.CsvFormats;
import io.micronaut.serde.csv.decoder.CsvToListDecoder;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.error.CsvParseException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decoder for inline-encoded CSV, expecting a single logical line of values.
 * @since 3.2.5
 */
@Singleton
@Secondary
@Internal
public final class InlineCsvDecoder extends AbstractCsvDecoder implements CsvToListDecoder {

    private final NoHeaderCsvDecoder noHeaderCsvDecoder;

    /**
     * Creates the inline CSV decoder.
     *
     * @param noHeaderCsvDecoder The decoder used for header-less inline CSV
     */
    public InlineCsvDecoder(NoHeaderCsvDecoder noHeaderCsvDecoder) {
        this.noHeaderCsvDecoder = noHeaderCsvDecoder;
    }

    @Override
    public Iterable<List<String>> decode(String value, CsvFormatConfig config) throws CsvException {
        List<org.apache.commons.csv.CSVRecord> records = new ArrayList<>();
        for (org.apache.commons.csv.CSVRecord record : decode(CsvFormats.INLINE, new StringReader(value))) {
            records.add(record);
        }
        if (records.size() > 1) {
            throw new CsvParseException("Expected a single line of encoded data");
        }
        List<List<String>> result = new ArrayList<>();
        if (!records.isEmpty()) {
            for (String field : records.getFirst().toList()) {
                for (List<String> line : noHeaderCsvDecoder.decode(field, config)) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    @Override
    public Iterable<List<String>> decode(InputStream input, Charset charset, CsvFormatConfig config) throws CsvException {
        List<org.apache.commons.csv.CSVRecord> records = new ArrayList<>();
        for (org.apache.commons.csv.CSVRecord record : decode(CsvFormats.INLINE, new InputStreamReader(input, charset))) {
            records.add(record);
        }
        if (records.size() > 1) {
            throw new CsvParseException("Expected a single line of encoded data");
        }
        List<List<String>> result = new ArrayList<>();
        if (!records.isEmpty()) {
            for (String field : records.getFirst().toList()) {
                for (List<String> line : noHeaderCsvDecoder.decode(field, config)) {
                    result.add(line);
                }
            }
        }
        return result;
    }

    @Override
    public Iterator<List<String>> decodeIterator(String value, CsvFormatConfig config) throws CsvException {
        return decode(value, config).iterator();
    }

    @Override
    public Iterator<List<String>> decodeIterator(InputStream input, Charset charset, CsvFormatConfig config) throws CsvException {
        return decode(input, charset, config).iterator();
    }
}
