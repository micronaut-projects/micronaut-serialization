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

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.csv.CsvObjectMapper;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

/**
 * Apache Commons CSV based implementation of {@link CsvObjectMapper}. Registers as a
 * secondary singleton bean and delegates encoding/decoding to Commons CSV backend classes.
 */
@Singleton
@Secondary
@Internal
public class ApacheCommonsCsvObjectMapper extends CsvObjectMapper {

    public ApacheCommonsCsvObjectMapper(SerdeRegistry registry,
                                        @Nullable SerdeConfiguration serdeConfiguration,
                                        @Nullable Class<?> view) {
        super(registry, serdeConfiguration, view);
    }

    @Override
    protected CsvObjectMapper newCsvObjectMapper(SerdeRegistry registry,
                                                @Nullable SerdeConfiguration configuration,
                                                @Nullable Class<?> view) {
        return new ApacheCommonsCsvObjectMapper(registry, configuration, view);
    }

    @Override
    protected io.micronaut.serde.Encoder newEncoder(OutputStream outputStream, Charset charset) {
        return new CommonsCsvEncoder(outputStream, null, charset);
    }

    @Override
    protected io.micronaut.serde.Decoder newDecoder(InputStream inputStream, Charset charset) {
        return new CommonsCsvDecoder(inputStream, charset);
    }

    @Override
    protected String encodeCsvLine(CsvFormatConfig format, List<@Nullable String> cells) throws IOException {
        return CsvFormats.encodeLine(format, cells);
    }

    @Override
    protected List<@Nullable String> decodeCsvLine(CsvFormatConfig format, String line) throws IOException {
        try (CSVParser parser = CsvFormats.parseLineFormat(format).parse(new StringReader(line))) {
            Iterator<CSVRecord> records = parser.iterator();
            return records.hasNext() ? records.next().toList() : List.of();
        }
    }
}
