package io.micronaut.serde.csv.apache.commons


import io.micronaut.serde.csv.apache.commons.mapper.ApacheCommonsCsvMapper
import io.micronaut.serde.csv.error.CsvException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CommonsCsvMapperSpec extends Specification {

    @Inject
    ApacheCommonsCsvMapper mapper

    void "test map records to pojo with default mapper"() throws CsvException {
        when:
        def input = "name,age\nAlice,30\nBob,25"
        def people = mapper.readAll(input, Person)

        then:
        people.count { true } == 2
        people[0].name() == "Alice"
        people[0].age() == 30
        people[1].name() == "Bob"
        people[1].age() == 25
    }

    void "test write maps to csv string with default mapper"() throws CsvException {
        when:
        def values = [
            [name: "Alice", age: "30"],
            [name: "Bob", age: "25"]
        ]
        def csv = mapper.writeToString(values)

        then:
        csv.trim().split('\n').size() == 2
        csv.contains("Alice,30")
        csv.contains("Bob,25")
    }

    void "test write maps to csv string with custom header"() throws CsvException {
        when:
        def values = [
            [name: "Alice", age: "30"],
            [name: "Bob", age: "25"]
        ]
        def csv = mapper.writeToString(values, ["name", "age"])

        then:
        csv.contains("Alice,30")
        csv.contains("Bob,25")
    }

    void "test read maps after manual header prepend"() throws CsvException {
        when:
        def value = [name: "Charlie", age: "40"]
        def csv = "name,age\n" + mapper.writeToString([value])
        def values = mapper.readAll(csv, Map)

        then:
        values.count { true } == 1
        values[0].name == "Charlie"
        values[0].age == "40"
    }

    void "test write as line with map value"() throws CsvException {
        when:
        def value = [name: "Alice", age: "30"]
        def line = mapper.writeAsLine(value)

        then:
        line instanceof List
        (line as List).contains("Alice")
        (line as List).contains("30")
    }

    void "test read from csv line with default mapper"() throws CsvException {
        when:
        def line = ["Alice", "30"]
        def person = mapper.read(line, Person)

        then:
        person.name() == "Alice"
        person.age() == 30
    }

    void "test read from iterable of csv lines"() throws CsvException {
        when:
        def lines = [
            ["Alice", "30"],
            ["Bob", "25"]
        ]
        def people = lines.collect { mapper.read(it, Person) }

        then:
        people.size() == 2
        people[0].name() == "Alice"
        people[1].name() == "Bob"
    }

    void "test read from input stream with default mapper"() throws CsvException {
        when:
        def input = new ByteArrayInputStream("name,age\nAlice,30\nBob,25".bytes)
        def people = mapper.read(input, "UTF-8", Person)

        then:
        people.count { true } == 2
        people[0].name() == "Alice"
        people[0].age() == 30
    }

    void "test roundtrip with special characters after manual header prepend"() throws CsvException {
        when:
        def value = [name: 'Alice "The Great"', age: "30"]
        def csv = "name,age\n" + mapper.writeToString([value])
        def values = mapper.readAll(csv, Map)

        then:
        values.count { true } == 1
        values[0].name == 'Alice "The Great"'
        values[0].age == "30"
    }
}
