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

import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper

import java.nio.charset.StandardCharsets

abstract class AbstractMicronautCsvSpec extends AbstractCsvSerdeSpec {

    abstract ObjectMapper getCsvMapper()

    // Overridden in Micronaut tests
    ObjectMapper getCsvMapperWithWriteHeader() {
        csvMapper
    }

    @Override
    def <T> T readCsv(String csv, Argument<T> type) {
        readCsv(csv.getBytes(StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readCsv(byte[] csv, Argument<T> type) {
        csvMapper.readValue(csv, type)
    }

    @Override
    def <T> T readCsv(InputStream csv, Argument<T> type) {
        csvMapper.readValue(csv, type)
    }

    @Override
    def <T> T readCsvWithHeader(String header, String csv, Argument<T> type) {
        readCsv(header + "\n" + csv, type)
    }

    @Override
    def <T> T readCsvDirect(String csv, Argument<T> type) {
        readCsv(csv, type)
    }

    @Override
    def <T> T readCsvWithHeaderDirect(String header, String csv, Argument<T> type) {
        readCsvWithHeader(header, csv, type)
    }

    @Override
    def <T> String writeCsv(Argument<T> type, T value) {
        new String(csvMapper.writeValueAsBytes(type, value), StandardCharsets.UTF_8)
    }

    @Override
    def <T> String writeCsvWithHeader(@Nullable String header, Argument<T> type, T value) {
        new String(csvMapperWithWriteHeader.writeValueAsBytes(type, value), StandardCharsets.UTF_8)
    }

    @Override
    def <T> String writeCsvWithInferredHeader(Argument<T> type, T value) {
        writeCsvWithHeader(null, type, value)
    }
}
