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
import io.micronaut.serde.csv.CsvObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
abstract class AbstractCsvLargeFileSpec extends Specification {

    @Inject
    CsvObjectMapper mapper

    static final String RESOURCE_PATH = "/data-awpUSFwaSojWKwzYoOV9C.csv"

    protected static String loadResource() {
        def is = AbstractCsvLargeFileSpec.class.getResourceAsStream(RESOURCE_PATH)
        assert is != null: "Resource not found: $RESOURCE_PATH"
        return is.text
    }

    // --- Map-based (dynamic header) reading ---

    void "decode large file (map) produces expected record count"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)

        then:
        records.count { true } == 500
    }

    void "decode large file (map) from input stream"() {
        when:
        def csv = loadResource()
        def input = new ByteArrayInputStream(csv.bytes)
        def records = mapper.readValues(input, Argument.of(Map, String, String))

        then:
        records.count { true } == 500
    }

    void "first map record has all expected headers"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)
        def first = records[0]

        then:
        ["alphanumeric", "total", "numberRange", "text", "region", "postalZip", "country", "list", "address", "email", "phone", "name"].every { first.containsKey(it) }
    }

    void "first map record has expected values"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)
        def first = records[0]

        then:
        first.alphanumeric == "KPK96YBX3JO"
        first.total == '$0.97'
        first.numberRange == "5"
        first.country == "Canada"
        first.name == "Lavinia Heath"
    }

    void "last map record has expected values"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)
        def last = records[-1]

        then:
        last.alphanumeric == "XAE60IYD9RX"
        last.country == "Germany"
        last.name == "Jin Cote"
    }

    void "all map records have non-null name"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)

        then:
        records.every { it.name != null }
    }

    void "filter map records by country finds matches"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)
        def canadaRecords = records.findAll { it.country == "Canada" }

        then:
        canadaRecords.size() > 0
        canadaRecords.every { it.country == "Canada" }
    }

    void "all map currency values are dollar prefixed"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)

        then:
        records.every { it.total?.startsWith('$') }
    }

    void "all map numberrange values are numeric"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, Map)

        then:
        records.every { it.numberRange?.matches('\\d+') }
    }

    // --- Pojo-based reading ---

    void "decode large file (pojo) produces expected record count"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.count { true } == 500
    }

    void "decode large file (pojo) from input stream"() {
        when:
        def csv = loadResource()
        def input = new ByteArrayInputStream(csv.bytes)
        def records = mapper.read(input, StandardCharsets.UTF_8.name(), CsvSample)

        then:
        records.count { true } == 500
    }

    void "first pojo record has expected values"() {
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

    void "last pojo record has expected values"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)
        def last = records[-1]

        then:
        last.alphanumeric() == "XAE60IYD9RX"
        last.country() == "Germany"
        last.name() == "Jin Cote"
    }

    void "first pojo record has values for all columns"() {
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

    void "all pojo records have non-null name"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.every { it.name() != null }
    }

    void "filter pojo records by country finds matches"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)
        def canadaRecords = records.findAll { it.country() == "Canada" }

        then:
        canadaRecords.size() > 0
        canadaRecords.every { it.country() == "Canada" }
    }

    void "all pojo currency values are dollar prefixed"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.every { it.total()?.currency()?.startsWith('$') }
    }

    void "all pojo numberrange values are numeric"() {
        when:
        def csv = loadResource()
        def records = mapper.readAll(csv, CsvSample)

        then:
        records.every { it.numberRange() instanceof Number }
    }
}
