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
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.format.FormattingTypeConverter;
import io.micronaut.core.convert.converters.MultiValuesConverterFactory;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts comma-separated rows to {@code List<List<String>>} arguments.
 * Jackson dataformat csv behaviour returns List<List<String>> for non defined schema table
 *
 * @see <a href="https://github.com/FasterXML/jackson-dataformats-text/blob/3.x/csv/src/test/java/tools/jackson/dataformat/csv/deser/BlogPost2021AprilTest.java">Jackson Test behavior</a>
 */
@Internal
@Singleton
public class CsvConverter implements FormattingTypeConverter<ConvertibleMultiValues<String>, List, Format> {

    @Override
    public Optional<List> convert(ConvertibleMultiValues<String> object,
                                  Class<List> targetType,
                                  ConversionContext context) {

        String name = context.getAnnotationMetadata()
            .stringValue(Bindable.class)
            .orElseGet(() -> argument(context).map(Argument::getName).orElse(MultiValuesConverterFactory.FORMAT_CSV));
        String csv = object.get(name);

        if (csv == null) {
            return Optional.empty();
        }

        return Optional.of(parse(csv, argument(context).orElse(Argument.listOf(String.class))));
    }

    @Override
    public Class<Format> annotationType() {
        return Format.class;
    }

    static List parse(String csv, Argument<?> targetType) {
        List<List<String>> rows = parseRows(csv);
        return rows;
    }

    static List<List<String>> parseRows(String csv) {
        return csv.lines()
            .filter(line -> !line.isBlank())
            .map(line -> Arrays.stream(line.split(",", -1))
                .map(String::trim)
                .collect(Collectors.toList()))
            .collect(Collectors.toList());
    }

    private static Optional<Argument<?>> argument(ConversionContext context) {
        if (context instanceof ArgumentConversionContext<?> argumentConversionContext) {
            return Optional.of(argumentConversionContext.getArgument());
        }
        return Optional.empty();
    }
}
