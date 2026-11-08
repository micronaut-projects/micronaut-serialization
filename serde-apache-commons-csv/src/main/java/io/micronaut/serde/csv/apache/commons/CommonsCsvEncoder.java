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
import io.micronaut.serde.csv.CsvEncoder;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Internal
public class CommonsCsvEncoder extends CsvEncoder {

    @Nullable
    private volatile CSVPrinter printer;
    @Nullable
    private final List<String> header;
    private final OutputStream outputStream;
    private volatile boolean headerWritten;

    public CommonsCsvEncoder(OutputStream outputStream, @Nullable List<String> header) {
        this.outputStream = outputStream;
        this.header = header;
    }

    private CSVPrinter printer() throws IOException {
        CSVPrinter p = printer;
        if (p == null) {
            synchronized (this) {
                p = printer;
                if (p == null) {
                    CSVFormat format = CSVFormat.DEFAULT.builder()
                        .setDelimiter(delimiter())
                        .setQuote(quoteChar())
                        .setEscape(escapeChar())
                        .setRecordSeparator(lineSeparator())
                        .setNullString(nullValue())
                        .get();
                    p = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), format);
                    if (header != null && !header.isEmpty()) {
                        p.printRecord((Object[]) header.toArray(new String[0]));
                    }
                    this.printer = p;
                    this.headerWritten = header != null && !header.isEmpty();
                }
            }
        }
        return p;
    }

    @Override
    public void writeHeader(List<String> header) throws IOException {
        if (!headerWritten && !header.isEmpty()) {
            synchronized (this) {
                if (!headerWritten && !header.isEmpty()) {
                    // Print the header without closing the printer: closing it would close the
                    // underlying OutputStreamWriter/outputStream, making subsequent flushRow()
                    // writes fail with "Stream closed". The printer (and stream) are closed by
                    // close() once all rows have been written.
                    printer().printRecord((Object[]) header.toArray(new String[0]));
                    headerWritten = true;
                }
            }
        }
    }

    @Override
    protected void flushRow() throws IOException {
        printer().printRecord(columnValues());
    }

    @Override
    public void close() throws IOException {
        CSVPrinter p;
        synchronized (this) {
            p = printer;
        }
        if (p != null) {
            p.close();
        }
    }
}
