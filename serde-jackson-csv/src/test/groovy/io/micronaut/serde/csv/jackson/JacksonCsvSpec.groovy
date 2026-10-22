package io.micronaut.serde.csv.jackson

import io.micronaut.serde.csv.jackson.JacksonCsvMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.io.ByteArrayInputStream

@MicronautTest
class JacksonCsvSpec extends Specification {

    @Inject
    JacksonCsvMapper csvMapper

    void "test read records from csv string"() {
        when:
        def input = "name,age\nAlice,30\nBob,25"
        def result = csvMapper.readAll(input, Person)

        then:
        result.count { true } == 2
        result[0].name() == "Alice"
        result[0].age() == 30
        result[1].name() == "Bob"
        result[1].age() == 25
    }

    void "test write records to csv string"() {
        when:
        def people = [
            new Person("Alice", 30),
            new Person("Bob", 25)
        ]
        def csv = csvMapper.writeToString(people)

        then:
        csv.trim().split('\n').size() == 2
        csv.contains("Alice,30")
        csv.contains("Bob,25")
    }

    void "test write as line"() {
        when:
        def person = new Person("Alice", 30)
        def line = csvMapper.writeAsLine(person)

        then:
        line instanceof List
        (line as List).contains("Alice")
        (line as List).contains("30")
    }

    void "test read records from input stream"() {
        when:
        def input = new ByteArrayInputStream("name,age\nAlice,30\nBob,25".bytes)
        def people = csvMapper.read(input, "UTF-8", Person).toList()

        then:
        people.size() == 2
        people[0].name() == "Alice"
        people[0].age() == 30
    }

    void "test write records to csv string does not include header by default"() {
        when:
        def people = [
            new Person("Alice", 30),
            new Person("Bob", 25)
        ]
        def csv = csvMapper.writeToString(people)

        then:
        !csv.contains("name,age")
        csv.contains("Alice,30")
        csv.contains("Bob,25")
    }

    void "test write empty collection"() {
        when:
        def csv = csvMapper.writeToString([])

        then:
        csv == ""
    }

    void "test write as line with null value"() {
        when:
        def line = csvMapper.writeAsLine(null)

        then:
        line instanceof List
        (line as List).isEmpty()
    }
}
