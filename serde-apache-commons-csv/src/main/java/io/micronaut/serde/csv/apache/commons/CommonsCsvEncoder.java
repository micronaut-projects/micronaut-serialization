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
    private CSVPrinter printer;
    @Nullable
    private final List<String> header;
    private final OutputStream outputStream;
    private boolean headerWritten;

    public CommonsCsvEncoder(OutputStream outputStream, @Nullable List<String> header) throws IOException {
        this.outputStream = outputStream;
        this.header = header;
    }

    @SuppressWarnings("NullAway")
    private CSVPrinter printer() throws IOException {
        if (printer == null) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter())
                .setQuote(quoteChar())
                .setEscape(escapeChar())
                .setRecordSeparator(lineSeparator())
                .setNullString(nullValue())
                .get();
            printer = new CSVPrinter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), format);
            if (header != null && !header.isEmpty()) {
                printer.printRecord((Object[]) header.toArray(new String[0]));
                headerWritten = true;
            }
        }
        return printer;
    }

    @Override
    public void writeHeader(List<String> header) throws IOException {
        if (!headerWritten && header != null && !header.isEmpty()) {
            printer().printRecord((Object[]) header.toArray(new String[0]));
            headerWritten = true;
        }
    }

    @Override
    protected void flushRow() throws IOException {
        printer().printRecord(columnValues());
    }

    @Override
    public void close() throws IOException {
        if (printer != null) {
            printer.close();
        }
    }
}
