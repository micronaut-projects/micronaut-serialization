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
package io.micronaut.serde.csv

import io.micronaut.core.type.Argument
import tools.jackson.databind.JavaType
import tools.jackson.dataformat.csv.CsvMapper
import tools.jackson.dataformat.csv.CsvReadFeature
import tools.jackson.dataformat.csv.CsvSchema

import java.nio.charset.StandardCharsets

abstract class AbstractJacksonDatabindCsvSpec extends AbstractCsvSerdeSpec {

    abstract CsvMapper getDatabindCsvMapper()

    @Override
    def <T> T readCsv(String csv, Argument<T> type) {
        databindCsvMapper.readerFor(toJavaType(type))
            .with(CsvReadFeature.WRAP_AS_ARRAY)
            .readValue(csv)
    }

    @Override
    def <T> T readCsv(byte[] csv, Argument<T> type) {
        readCsv(new String(csv, StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readCsv(InputStream csv, Argument<T> type) {
        databindCsvMapper.readerFor(toJavaType(type))
            .with(CsvReadFeature.WRAP_AS_ARRAY)
            .readValue(csv)
    }

    @Override
    def <T> T readCsvWithHeader(String header, String csv, Argument<T> type) {
        def rowType = rowType(type)
        if (rowType != null) {
            return (T) databindCsvMapper.readerFor(rowType)
                .with(CsvSchema.emptySchema().withHeader())
                .readValues(header + "\n" + csv)
                .readAll()
        }
        databindCsvMapper.readerFor(toJavaType(type))
            .with(CsvSchema.emptySchema().withHeader())
            .readValue(header + "\n" + csv)
    }

    private JavaType rowType(Argument<?> type) {
        def rowArgument = rowArgument(type)
        rowArgument == null ? null : toJavaType(rowArgument)
    }

    private Argument<?> rowArgument(Argument<?> type) {
        if (Iterable.isAssignableFrom(type.type)) {
            return type.firstTypeVariable.orElse(null)
        }
        null
    }

    private JavaType toJavaType(Argument<?> argument) {
        if (!argument.typeParameters) {
            return databindCsvMapper.typeFactory.constructType(argument.type)
        }
        return databindCsvMapper.typeFactory.constructParametricType(
            argument.type,
            argument.typeParameters.collect { toJavaType(it) } as JavaType[]
        )
    }
}
