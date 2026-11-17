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
package io.micronaut.serde.csv;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Backend SPI for encoding a single CSV record. Implementations delegate the CSV quoting and
 * escaping to the backend library.
 *
 * @since 3.2.5
 */
@FunctionalInterface
@Internal
public interface CsvLineEncoder {

    /**
     * Encodes logical cell values as one CSV record.
     *
     * @param format The resolved CSV format
     * @param cells  The logical cell values
     * @return The encoded record without a trailing line separator
     * @throws IOException If the record cannot be encoded
     */
    String encodeLine(CsvFormatConfig format, List<@Nullable String> cells) throws IOException;
}
