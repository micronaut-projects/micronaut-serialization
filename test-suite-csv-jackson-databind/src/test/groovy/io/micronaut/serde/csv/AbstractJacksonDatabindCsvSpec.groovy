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

import io.micronaut.core.beans.BeanIntrospection
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
            .with(CsvReadFeature.SKIP_EMPTY_LINES)
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
            .with(CsvReadFeature.SKIP_EMPTY_LINES)
            .readValue(csv)
    }

    @Override
    def <T> T readCsvWithHeader(String header, String csv, Argument<T> type) {
        def schema = schema(header)
        def rowType = rowType(type)
        if (rowType != null) {
            return (T) databindCsvMapper.readerFor(rowType)
                .with(schema)
                .with(CsvReadFeature.SKIP_EMPTY_LINES)
                .readValues(csv)
                .readAll()
        }
        databindCsvMapper.readerFor(toJavaType(type))
            .with(schema)
            .with(CsvReadFeature.SKIP_EMPTY_LINES)
            .readValue(csv)
    }

    @Override
    def <T> T readCsvDirect(String csv, Argument<T> type) {
        databindCsvMapper.rebuild()
            .enable(CsvReadFeature.WRAP_AS_ARRAY)
            .enable(CsvReadFeature.SKIP_EMPTY_LINES)
            .build()
            .readValue(csv, toJavaType(type))
    }

    @Override
    def <T> T readCsvWithHeaderDirect(String header, String csv, Argument<T> type) {
        def schema = schema(header)
        def rowType = rowType(type)
        if (rowType != null) {
            return (T) databindCsvMapper.reader(schema)
                .with(CsvReadFeature.SKIP_EMPTY_LINES)
                .forType(rowType)
                .readValues(csv)
                .readAll()
        }
        databindCsvMapper.reader(schema)
            .with(CsvReadFeature.SKIP_EMPTY_LINES)
            .forType(toJavaType(type))
            .readValue(csv)
    }

    @Override
    def <T> String writeCsv(Argument<T> type, T value) {
        databindCsvMapper.writer(CsvSchema.emptySchema().withLineSeparator("\n"))
            .forType(toJavaType(type))
            .writeValueAsString(value)
    }

    @Override
    def <T> String writeCsvWithHeader(String header, Argument<T> type, T value) {
        databindCsvMapper.writer(schema(header).withHeader())
            .forType(toJavaType(type))
            .writeValueAsString(value)
    }

    @Override
    def <T> String writeCsvWithInferredHeader(Argument<T> type, T value) {
        databindCsvMapper.writer(schema(type).withHeader())
            .forType(toJavaType(type))
            .writeValueAsString(value)
    }

    private CsvSchema schema(String header) {
        def schemaBuilder = CsvSchema.builder()
        header.split(",", -1)
            .collect { it.trim() }
            .each { schemaBuilder.addColumn(it) }
        schemaBuilder.build().withLineSeparator("\n")
    }

    private CsvSchema schema(Argument<?> type) {
        def rowArgument = rowArgument(type) ?: type
        def schemaBuilder = CsvSchema.builder()
        BeanIntrospection.getIntrospection(rowArgument.type)
            .beanProperties
            .each { schemaBuilder.addColumn(it.name) }
        schemaBuilder.build().withLineSeparator("\n")
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
