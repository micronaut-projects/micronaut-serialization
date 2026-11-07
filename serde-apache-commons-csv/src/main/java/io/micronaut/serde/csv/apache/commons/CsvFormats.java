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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-configured {@link CSVFormat} instances used by the CSV serde.
 * @since 3.2.5
 */
@Internal
public final class CsvFormats {

    /**
     * Default format with a header row, skipped on read and empty string nulls.
     */
    public static final CSVFormat DEFAULT = CSVFormat.DEFAULT
        .builder()
        .setIgnoreEmptyLines(true)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setNullString("")
        .get();

    private CsvFormats() {
    }

    /**
     * Cache of parser formats built by {@link #from(CsvFormatConfig)}: header-aware,
     * multi-line document parsing (attaches header columns and skips the header record
     * when {@link CsvFormatConfig#hasHeader()} is true).
     */
    private static final ConcurrentHashMap<CsvFormatConfig, CSVFormat> FROM_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache of parser formats built by {@link #parseLineFormat(CsvFormatConfig)}: single
     * inline record parsing with no header handling and empty lines preserved
     * ({@code ignoreEmptyLines=false}).
     */
    private static final ConcurrentHashMap<CsvFormatConfig, CSVFormat> PARSE_LINE_CACHE = new ConcurrentHashMap<>();

    /**
     * Key for {@link #FORMAT_CACHE}: a resolved config plus whether the printer should
     * attach a header row.
     */
    private record FormatKey(CsvFormatConfig config, boolean withHeader) {}

    /**
     * Cache of printer formats built by {@link #format(CsvFormatConfig, boolean)}:
     * encoding records, optionally with a header row.
     */
    private static final ConcurrentHashMap<FormatKey, CSVFormat> FORMAT_CACHE = new ConcurrentHashMap<>();

    /**
     * Encodes a single record without a header.
     *
     * @param config The resolved CSV format
     * @param values The record values
     * @return The encoded record without the trailing line separator
     * @throws CsvException If the record cannot be encoded
     */
    public static String encodeLine(CsvFormatConfig config, Iterable<? extends @Nullable String> values) throws CsvException {
        // Estimate a capacity so the StringWriter's backing buffer does not grow repeatedly.
        int capacity = 64;
        for (@Nullable String v : values) {
            if (v != null) {
                capacity += v.length() + 2;
            }
        }
        StringWriter writer = new StringWriter(capacity);
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
        return FROM_CACHE.computeIfAbsent(config, c -> {
            CSVFormat.Builder builder = apply(c);
            if (c.hasHeader()) {
                if (c.explicitColumns()) {
                    builder.setHeader(c.columns().toArray(new String[0]));
                } else {
                    builder.setHeader();
                }
                builder.setSkipHeaderRecord(true);
            }
            return builder.get();
        });
    }

    /**
     * Builds a parser {@link CSVFormat} for a single inline record. Header handling is not
     * applied because an inline record never contains a header.
     *
     * @param config The resolved CSV format
     * @return The Apache Commons CSV format
     */
    public static CSVFormat parseLineFormat(CsvFormatConfig config) {
        return PARSE_LINE_CACHE.computeIfAbsent(config, c -> apply(c)
            .setIgnoreEmptyLines(false)
            .get());
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
        FormatKey key = new FormatKey(config, withHeader);
        return FORMAT_CACHE.computeIfAbsent(key, k -> {
            CSVFormat.Builder builder = apply(k.config());
            if (k.withHeader() && !k.config().columns().isEmpty()) {
                builder.setHeader(k.config().columns().toArray(new String[0]));
            }
            return builder.get();
        });
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
        CSVFormat.Builder builder = CSVFormat.DEFAULT
            .builder()
            .setDelimiter(config.delimiter())
            .setQuote(config.quote())
            .setEscape(config.escape())
            .setRecordSeparator(config.lineSeparator())
            .setNullString(config.nullValue())
            .setIgnoreEmptyLines(config.skipEmptyLines())
            .setIgnoreSurroundingSpaces(config.trim())
            .setQuoteMode(QuoteMode.MINIMAL);
        if (config.allowComments()) {
            builder.setCommentMarker('#');
        }
        return builder;
    }
}
