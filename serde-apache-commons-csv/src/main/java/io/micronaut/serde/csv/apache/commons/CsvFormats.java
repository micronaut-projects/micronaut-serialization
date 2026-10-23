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
package io.micronaut.serde.csv.apache.commons;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;

/**
 * Pre-configured {@link CSVFormat} instances used by the CSV serde.
 * @since 3.2.5
 */
@Internal
public final class CsvFormats {

    /**
     * Default format with a header row, skipped on read, {@code #} comment marker and empty string nulls.
     */
    public static final CSVFormat DEFAULT = CSVFormat.DEFAULT
        .builder()
        .setIgnoreEmptyLines(true)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setNullString("")
        .setCommentMarker('#')
        .get();

    private CsvFormats() {
    }

    /**
     * Encodes a single record without a header.
     *
     * @param config The resolved CSV format
     * @param values The record values
     * @return The encoded record without the trailing line separator
     * @throws CsvException If the record cannot be encoded
     */
    public static String encodeLine(CsvFormatConfig config, Iterable<? extends @Nullable String> values) throws CsvException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, format(config, false))) {
            printer.printRecord(values);
        } catch (IOException e) {
            throw new CsvException("Failed to encode CSV line: " + e.getMessage(), e);
        }
        String encoded = writer.toString();
        String lineSeparator = config.lineSeparator();
        return encoded.endsWith(lineSeparator)
            ? encoded.substring(0, encoded.length() - lineSeparator.length())
            : encoded;
    }

    /**
     * Builds a parser {@link CSVFormat} from a resolved {@link CsvFormatConfig}.
     *
     * @param config The resolved CSV format
     * @return The Apache Commons CSV format
     */
    public static CSVFormat from(CsvFormatConfig config) {
        CSVFormat.Builder builder = apply(config);
        if (config.hasHeader()) {
            if (config.explicitColumns()) {
                builder.setHeader(config.columns().toArray(new String[0]));
            } else {
                builder.setHeader();
            }
            builder.setSkipHeaderRecord(true);
        }
        return builder.get();
    }

    /**
     * Builds a printer {@link CSVFormat} from a resolved {@link CsvFormatConfig}. The header
     * is attached when {@link CsvFormatConfig#writeHeader()} is {@code true}.
     *
     * @param config The resolved CSV format
     * @return The Apache Commons CSV format
     */
    public static CSVFormat writeFormat(CsvFormatConfig config) {
        return format(config, config.writeHeader());
    }

    /**
     * Builds a printer {@link CSVFormat} from a resolved {@link CsvFormatConfig}.
     *
     * @param config     The resolved CSV format
     * @param withHeader Whether the column header should be attached
     * @return The Apache Commons CSV format
     */
    public static CSVFormat format(CsvFormatConfig config, boolean withHeader) {
        CSVFormat.Builder builder = apply(config);
        if (withHeader && !config.columns().isEmpty()) {
            builder.setHeader(config.columns().toArray(new String[0]));
        }
        return builder.get();
    }

    /**
     * Builds a printer {@link CSVFormat} from a resolved {@link CsvFormatConfig} with an explicit
     * header.
     *
     * @param config The resolved CSV format
     * @param header The explicit header columns
     * @return The Apache Commons CSV format
     */
    public static CSVFormat format(CsvFormatConfig config, Collection<String> header) {
        CSVFormat.Builder builder = apply(config);
        builder.setHeader(header.toArray(new String[0]));
        return builder.get();
    }

    private static CSVFormat.Builder apply(CsvFormatConfig config) {
        return CSVFormat.DEFAULT
            .builder()
            .setDelimiter(config.delimiter())
            .setQuote(config.quote())
            .setEscape(config.escape())
            .setRecordSeparator(config.lineSeparator())
            .setNullString(config.nullValue())
            .setIgnoreEmptyLines(config.skipEmptyLines())
            .setIgnoreSurroundingSpaces(config.trim())
            .setQuoteMode(QuoteMode.MINIMAL);
    }
}
