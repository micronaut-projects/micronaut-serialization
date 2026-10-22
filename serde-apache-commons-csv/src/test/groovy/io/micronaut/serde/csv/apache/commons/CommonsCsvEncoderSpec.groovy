package io.micronaut.serde.csv.apache.commons

import io.micronaut.serde.csv.apache.commons.encoder.DefaultCsvEncoder
import io.micronaut.serde.csv.apache.commons.encoder.NoHeaderCsvEncoder
import io.micronaut.serde.csv.error.CsvException
import io.micronaut.serde.csv.introspection.CsvFormatConfig
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CommonsCsvEncoderSpec extends Specification {

    @Inject
    DefaultCsvEncoder defaultEncoder

    @Inject
    NoHeaderCsvEncoder noHeaderEncoder

    private static final CsvFormatConfig DEFAULT_CONFIG = CsvFormatConfig.DEFAULT

    void "test encode records with default encoder and header"() {
        when:
        def values = [
            [name: "Alice", age: "30"],
            [name: "Bob", age: "25"]
        ]
        def result = defaultEncoder.encodeToString(values, ["name", "age"], DEFAULT_CONFIG)

        then:
        result.trim().split('\n').size() == 2
        result.contains("Alice,30")
        result.contains("Bob,25")
    }

    void "test encode records with default encoder without explicit header"() throws CsvException {
        when:
        def values = [
            [name: "Alice", age: "30"],
            [name: "Bob", age: "25"]
        ]
        def result = defaultEncoder.encodeToString(values, DEFAULT_CONFIG)

        then:
        result.trim().split('\n').size() == 2
    }

    void "test encode single record with default encoder"() throws CsvException {
        when:
        def result = defaultEncoder.encodeToString([[name: "Alice", age: "30"]], ["name", "age"], DEFAULT_CONFIG)

        then:
        result.trim() == "Alice,30"
    }

    void "test encode headerless csv with no header encoder"() throws CsvException {
        when:
        def values = [
            ["Alice", "30"],
            ["Bob", "25"]
        ]
        def result = noHeaderEncoder.encodeToString(values, DEFAULT_CONFIG)

        then:
        result.trim().split('\n').size() == 2
        result.contains("Alice,30")
        result.contains("Bob,25")
    }

    void "test encode headerless single value"() throws CsvException {
        when:
        def result = noHeaderEncoder.encodeToString([["Alice", "30"]], DEFAULT_CONFIG)

        then:
        result.trim() == "Alice,30"
    }
}
