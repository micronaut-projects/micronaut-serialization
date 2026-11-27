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
package io.micronaut.serde.csv.jackson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import tools.jackson.dataformat.csv.CsvSchema;

/**
 * Builds Jackson CSV schemas from a resolved {@link CsvFormatConfig}.
 *
 * @since 3.2.5
 */
@Internal
final class JacksonCsvFormats {

    private JacksonCsvFormats() {
    }

    static CsvSchema.Builder builder(CsvFormatConfig config, boolean useHeader) {
        return CsvSchema.builder()
            .setUseHeader(useHeader)
            .setColumnSeparator(config.delimiter())
            .setQuoteChar(config.quote())
            .setEscapeChar(config.escape())
            .setLineSeparator(config.lineSeparator())
            .setNullValue(config.nullValue())
            .setAllowComments(config.allowComments());
    }

    static CsvSchema lineSchema(CsvFormatConfig config) {
        return builder(config, false).build();
    }

    static String stripLineSeparator(String csv, String lineSeparator) {
        return csv.endsWith(lineSeparator)
            ? csv.substring(0, csv.length() - lineSeparator.length())
            : csv;
    }
}
