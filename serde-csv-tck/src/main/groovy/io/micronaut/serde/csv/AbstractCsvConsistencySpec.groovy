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

import io.micronaut.serde.csv.error.CsvException
import io.micronaut.serde.csv.mapper.CsvMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
abstract class AbstractCsvConsistencySpec extends Specification {

    @Inject
    CsvMapper mapper

    void "csv property rename order and ignore are honored when writing"() {
        when:
        def csv = mapper.writeToString(
            [new CsvMappedNoFormat("C", "A", "ignored", "B")],
            ["a_renamed", "b", "c"]
        )

        then:
        csv.trim() == "a_renamed,b,c\nA,B,C"
    }

    void "csv property rename order and ignore are honored when reading"() {
        when:
        def values = mapper.readAll("a_renamed,b,c\nA,B,C\n", CsvMappedNoFormat).toList()

        then:
        values == [new CsvMappedNoFormat("C", "A", null, "B")]
    }

    void "write as line uses the configured column order"() {
        when:
        def line = mapper.writeAsLine(new CsvMappedNoFormat("C", "A", "ignored", "B"))

        then:
        line == ["A", "B", "C"]
    }

    void "property-level CsvFormat encodes a nested bean inline"() {
        given:
        def value = new CsvInlineProperty("1", new Person("Alice", 30))

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll("id,person\n1,Alice|30\n", CsvInlineProperty).toList()

        then:
        csv.trim() == "1,Alice|30"
        line == ["1", "Alice|30"]
        values == [value]
    }

    void "property-level CsvFormat on a JavaBean field encodes a nested bean inline"() {
        given:
        def value = new CsvClassInlineProperty("1", new CsvBeanPerson("Alice", 30))

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll("id,person\n1,Alice|30\n", CsvClassInlineProperty).toList()

        then:
        csv.trim() == "1,Alice|30"
        line == ["1", "Alice|30"]
        values == [value]
    }

    void "property-level CsvFormat with JsonProperty encodes a nested bean inline"() {
        given:
        def value = new CsvJsonPropertyInlineProperty("Hello, CSV!", new CsvBeanPerson("foo", 1))

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll('message,inner_data\n"Hello, CSV!",foo|1\n', CsvJsonPropertyInlineProperty).toList()

        then:
        csv.trim() == '"Hello, CSV!",foo|1'
        line == ["Hello, CSV!", "foo|1"]
        values == [value]
    }

    void "property-level CsvFormat quotes inline delimiter values"() {
        given:
        def value = new CsvInlineProperty("1", new Person("Alice|Bob", 30))

        when:
        def line = mapper.writeAsLine(value)
        def csv = mapper.writeToString([value], ["id", "person"])
        def values = mapper.readAll(csv, CsvInlineProperty).toList()

        then:
        line == ["1", "\"Alice|Bob\"|30"]
        values == [value]
    }

    void "property-level CsvFormat on record component encodes a nested bean inline"() {
        given:
        def value = new CsvRecordComponentFormat(new CsvRecordComponentFormat.Name("John", "Doe"), 25)

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll("full_name,age\nJohn|Doe,25\n", CsvRecordComponentFormat).toList()

        then:
        csv.trim() == "John|Doe,25"
        line == ["John|Doe", "25"]
        values == [value]
    }

    void "multiple inline properties with different delimiters"() {
        given:
        def value = new CsvMultiInlineProperty(
            "1",
            new Person("Alice", 30),
            new CsvMultiInlineProperty.Address("New York", "10001")
        )

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll("id,person,address_info\n1,Alice|30,New York;10001\n", CsvMultiInlineProperty).toList()

        then:
        csv.replace('"', '').trim() == "1,Alice|30,New York;10001"
        line == ["1", "Alice|30", "New York;10001"]
        values == [value]
    }

    void "nested inline properties (recursive inline encoding)"() {
        given:
        def ceo = new CsvNestedInlineProperty.Company.Employee("Bob", 45)
        def company = new CsvNestedInlineProperty.Company("Acme Corp", ceo)
        def value = new CsvNestedInlineProperty("1", company)

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll("id,company\n1,Acme Corp|Bob;45\n", CsvNestedInlineProperty).toList()

        then:
        csv.contains("Acme Corp|Bob;45")
        !csv.contains("company")
        line == ["1", "Acme Corp|Bob;45"]
        values == [value]
    }

    void "CsvFormat with Header ABSENT writes and reads without header"() {
        given:
        def value = new CsvNoHeader("Alice", 30)

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll(csv, CsvNoHeader).toList()

        then:
        !csv.contains("name")
        line == ["Alice", "30"]
        values == [value]
    }

    void "CsvFormat with nullValue writes NULL for null fields"() {
        given:
        def value = new CsvWithNullAndTrim("Alice", null)

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)

        then:
        csv.trim() == "name,age\nAlice,NULL"
        line == ["Alice", null]
    }

    void "CsvFormat with trim trims whitespace when reading"() {
        when:
        def values = mapper.readAll("name,age\n  Alice  , 30  \n", CsvWithNullAndTrim).toList()

        then:
        values.size() == 1
        values[0].name == "Alice"
        values[0].age == 30
    }

    void "CsvFormat with custom quote character"() {
        given:
        def value = new CsvWithNullAndTrim("Alice, Jr.", 30)

        when:
        def csv = mapper.writeToString([value])
        def values = mapper.readAll(csv, CsvWithNullAndTrim).toList()

        then:
        csv.trim() == "name,age\n'Alice, Jr.',30"
        values == [value]
    }

    void "inline property with null value"() {
        given:
        def value = new CsvInlineWithNull("1", null)

        when:
        def csv = mapper.writeToString([value])
        def line = mapper.writeAsLine(value)
        def values = mapper.readAll("id,person\n1,\n", CsvInlineWithNull).toList()

        then:
        csv.trim() == "1,"
        line.size() == 2
        line[0] == "1"
        line[1] == null
        values[0].id == "1"
        values[0].person == null
    }

    void "writing a null record fails with a mapping exception"() {
        when:
        mapper.writeToString([new Person("Alice", 30), null])

        then:
        thrown(CsvException)
    }

    void "writing a null map key fails with a mapping exception"() {
        given:
        def record = new LinkedHashMap()
        record.put(null, "value")

        when:
        mapper.writeToString([record])

        then:
        thrown(CsvException)
    }

    void "skips empty lines by default"() {
        when:
        def values = mapper.readAll("name,age\n\nAlice,30\n", Person).toList()

        then:
        values == [new Person("Alice", 30)]
    }

    void "reads a single line by column position"() {
        when:
        def value = mapper.read(["Alice", "30"], Person)

        then:
        value == new Person("Alice", 30)
    }

    void "writes an explicit header"() {
        when:
        def csv = mapper.writeToString([new Person("Alice", 30)], ["name", "age"])

        then:
        csv.trim() == "name,age\nAlice,30"
    }

    void "reads and writes maps without a bean type"() {
        when:
        def values = mapper.readAll("name,age\nAlice,30\n", Map).toList()
        def csv = mapper.writeToString([[name: "Alice", age: 30]])
        def line = mapper.writeAsLine([name: "Alice", age: 30])

        then:
        values == [[name: "Alice", age: "30"]]
        csv.trim() == "Alice,30"
        line == ["Alice", "30"]
    }

    void "string-backed records are parsed lazily"() {
        when:
        def values = mapper.readAll("name,age\nAlice,30\nBob,not-a-number", Person)
        def iterator = values.iterator()

        then:
        iterator.next() == new Person("Alice", 30)

        when:
        iterator.next()

        then:
        thrown(RuntimeException)
    }

    void "string-backed records can be iterated independently"() {
        when:
        def values = mapper.readAll("a_renamed,b,c\nA,B,C\nD,E,F\n", CsvMappedNoFormat)
        def first = values.iterator()
        def second = values.iterator()

        then:
        first.next() == new CsvMappedNoFormat("C", "A", null, "B")
        first.next() == new CsvMappedNoFormat("F", "D", null, "E")
        second.next() == new CsvMappedNoFormat("C", "A", null, "B")
    }

    void "row iterables are consumed lazily"() {
        given:
        int consumed = 0
        Iterable<List<String>> rows = () -> new Iterator<List<String>>() {
            private int index

            @Override
            boolean hasNext() {
                index < 2
            }

            @Override
            List<String> next() {
                consumed++
                index++
                index == 1 ? ["Alice", "30"] : ["Bob", "25"]
            }
        }

        when:
        def values = mapper.read(rows, Person)

        then:
        consumed == 0

        when:
        def first = values.iterator().next()

        then:
        consumed == 1
        first == new Person("Alice", 30)
    }

    void "reads from InputStream with charset"() {
        given:
        def csvString = "name,age\nAlice,30\nBob,25\n"
        def inputStream = new ByteArrayInputStream(csvString.bytes)

        when:
        def values = mapper.read(inputStream, "UTF-8", Person).toList()

        then:
        values == [new Person("Alice", 30), new Person("Bob", 25)]
    }

    void "reads single value from InputStream"() {
        given:
        def csvString = "name,age\nAlice,30\n"
        def inputStream = new ByteArrayInputStream(csvString.bytes)

        when:
        def value = mapper.read(inputStream, "UTF-8", Person).iterator().next()

        then:
        value == new Person("Alice", 30)
    }

    void "writes to OutputStream"() {
        given:
        def value = new Person("Alice", 30)
        def outputStream = new ByteArrayOutputStream()

        when:
        mapper.writeValue(outputStream, value, StandardCharsets.UTF_8)
        def csv = outputStream.toString(StandardCharsets.UTF_8)

        then:
        csv.trim() == "Alice,30"
    }

    void "writes multiple values to OutputStream"() {
        given:
        def values = [new Person("Alice", 30), new Person("Bob", 25)]
        def outputStream = new ByteArrayOutputStream()

        when:
        mapper.writeValues(outputStream, values, StandardCharsets.UTF_8)
        def csv = outputStream.toString(StandardCharsets.UTF_8)

        then:
        csv.trim() == "Alice,30\nBob,25"
    }
}