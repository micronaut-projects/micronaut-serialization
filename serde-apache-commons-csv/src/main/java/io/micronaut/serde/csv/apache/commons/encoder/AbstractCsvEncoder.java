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

import io.micronaut.serde.csv.error.CsvException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;

/**
 * Base class for CSV encoders backed by Apache Commons CSV.
 * @since 3.2.5
 */
@Internal
public abstract class AbstractCsvEncoder {

    protected final String encodeToString(CSVFormat format, Iterable<? extends Collection<@Nullable String>> values) throws CsvException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            printer.printRecords(values);
        } catch (IOException e) {
            throw new CsvException("Failed to encode CSV: " + e.getMessage(), e);
        }
        return writer.toString();
    }
}
