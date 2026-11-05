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

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.csv.CsvObjectMapper;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Secondary
@Internal
public class JacksonCsvObjectMapper extends CsvObjectMapper {

    private final CsvMapper csvMapper = new CsvMapper();

    public JacksonCsvObjectMapper(SerdeRegistry registry,
                                 @Nullable SerdeConfiguration serdeConfiguration,
                                 @Nullable Class<?> view) {
        super(registry, serdeConfiguration, view);
    }

    @Override
    protected CsvObjectMapper newCsvObjectMapper(SerdeRegistry registry,
                                                @Nullable SerdeConfiguration configuration,
                                                @Nullable Class<?> view) {
        return new JacksonCsvObjectMapper(registry, configuration, view);
    }

    @Override
    protected io.micronaut.serde.Encoder newEncoder(OutputStream outputStream) {
        return new JacksonCsvEncoder(outputStream);
    }

    @Override
    protected io.micronaut.serde.Decoder newDecoder(InputStream inputStream) {
        return new JacksonCsvDecoder(inputStream);
    }

    @Override
    protected String encodeCsvLine(CsvFormatConfig format, List<@Nullable String> cells) throws IOException {
        CsvSchema.Builder builder = JacksonCsvFormats.builder(format, false);
        Map<String, @Nullable String> record = new LinkedHashMap<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            String name = "c" + i;
            builder.addColumn(name);
            record.put(name, cells.get(i));
        }
        String csv = csvMapper.writer(builder.build())
            .with(tools.jackson.dataformat.csv.CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
            .writeValueAsString(record);
        return JacksonCsvFormats.stripLineSeparator(csv, format.lineSeparator());
    }

    @Override
    protected List<@Nullable String> decodeCsvLine(CsvFormatConfig format, String line) throws IOException {
        if (line.isEmpty()) {
            return List.of();
        }
        CsvSchema schema = JacksonCsvFormats.lineSchema(format);
        return csvMapper.readerForListOf(String.class).with(schema).readValue(line);
    }
}
