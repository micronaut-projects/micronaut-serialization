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

import io.micronaut.serde.csv.error.CsvParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for CSV decoders backed by Apache Commons CSV.
 * @since 3.2.5
 */
@Internal
public abstract class AbstractCsvDecoder {

    protected final Iterable<CSVRecord> decode(CSVFormat format, Reader reader) throws CsvParseException {
        try {
            return format.parse(reader);
        } catch (IOException e) {
            throw new CsvParseException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a lazy iterable that opens a parser for every iterator. Supplying a fresh reader
     * per iterator makes string-backed CSV repeatable and safe for independent iteration.
     *
     * @param format         The CSV format
     * @param readerSupplier Supplies a reader for each iterator
     * @param mapper         Maps each CSV record to a value
     * @param <T>            The value type
     * @return The lazy iterable
     */
    protected final <T> Iterable<T> decode(CSVFormat format,
                                           Supplier<Reader> readerSupplier,
                                           Function<CSVRecord, T> mapper) {
        return () -> new RecordIterator<>(format, readerSupplier.get(), mapper);
    }

    private static final class RecordIterator<T> implements Iterator<T> {
        private final Function<CSVRecord, T> mapper;
        private final CSVParser parser;
        private final Iterator<CSVRecord> records;
        private boolean closed;

        private RecordIterator(CSVFormat format, Reader reader, Function<CSVRecord, T> mapper) {
            this.mapper = mapper;
            try {
                this.parser = format.parse(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to parse CSV", e);
            }
            this.records = parser.iterator();
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }
            boolean hasNext = records.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return mapper.apply(records.next());
        }

        private void close() {
            if (!closed) {
                closed = true;
                try {
                    parser.close();
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to close CSV parser", e);
                }
            }
        }
    }
}
