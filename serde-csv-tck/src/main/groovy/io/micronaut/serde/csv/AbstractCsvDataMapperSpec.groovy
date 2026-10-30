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

import io.micronaut.serde.csv.mapper.CsvMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
abstract class AbstractCsvDataMapperSpec extends Specification {

    @Inject
    CsvMapper mapper

    static final String RESOURCE_PATH = "/data-awpUSFwaSojWKwzYoOV9C.csv"

    protected static String loadResource() {
        def is = AbstractCsvDataMapperSpec.class.getResourceAsStream(RESOURCE_PATH)
        assert is != null: "Resource not found: $RESOURCE_PATH"
        return is.text
    }

    void "decode large file produces expected record count"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.count { true } == 500
    }

    void "decode large file from input stream"() {
        when:
        def csv = loadResource()
        def input = new ByteArrayInputStream(csv.bytes)
        def records = mapper.read(input, StandardCharsets.UTF_8.name(), CsvSample)

        then:
        records.count { true } == 500
    }

    void "first record has expected values"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)
        def first = records[0]

        then:
        first.alphanumeric() == "KPK96YBX3JO"
        first.total().currency() == '$'
        first.total().amount() == 0.97
        first.numberRange() == 5
        first.country() == "Canada"
        first.name() == "Lavinia Heath"
    }

    void "last record has expected values"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)
        def last = records[-1]

        then:
        last.alphanumeric() == "XAE60IYD9RX"
        last.country() == "Germany"
        last.name() == "Jin Cote"
    }

    void "first record has values for all columns"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)
        def first = records[0]

        then:
        first.alphanumeric() != null
        first.total() != null
        first.numberRange() != null
        first.text() != null
        first.region() != null
        first.postalZip() != null
        first.country() != null
        first.list() != null
        first.address() != null
        first.email() != null
        first.phone() != null
        first.name() != null
    }

    void "all records have non-null name"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.every { it.name() != null }
    }

    void "filter records by country finds matches"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)
        def canadaRecords = records.findAll { it.country() == "Canada" }

        then:
        canadaRecords.size() > 0
        canadaRecords.every { it.country() == "Canada" }
    }

    void "all currency values are dollar prefixed"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.every { it.total()?.currency()?.startsWith('$') }
    }

    void "all numberrange values are numeric"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.every { it.numberRange() instanceof Number }
    }
}
