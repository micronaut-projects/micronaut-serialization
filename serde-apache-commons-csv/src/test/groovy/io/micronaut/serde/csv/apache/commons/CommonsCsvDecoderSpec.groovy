package io.micronaut.serde.csv.apache.commons

import io.micronaut.serde.csv.apache.commons.decoder.DefaultCsvDecoder
import io.micronaut.serde.csv.apache.commons.decoder.InlineCsvDecoder
import io.micronaut.serde.csv.apache.commons.decoder.NoHeaderCsvDecoder
import io.micronaut.serde.csv.error.CsvException
import io.micronaut.serde.csv.introspection.CsvFormatConfig
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CommonsCsvDecoderSpec extends Specification {

    @Inject
    DefaultCsvDecoder defaultDecoder

    @Inject
    NoHeaderCsvDecoder noHeaderDecoder

    @Inject
    InlineCsvDecoder inlineDecoder

    private static final CsvFormatConfig DEFAULT_CONFIG = CsvFormatConfig.DEFAULT

    void "test decode simple csv with default decoder"() throws CsvException {
        when:
        def records = defaultDecoder.decode("name,age\nAlice,30\nBob,25", DEFAULT_CONFIG)

        then:
        records.count { true } == 2
        records[0] == [name: "Alice", age: "30"]
        records[1] == [name: "Bob", age: "25"]
    }

    void "test decode csv with custom header using default decoder"() {
        when:
        def records = defaultDecoder.decode("30,Alice\n25,Bob", ["age", "name"])

        then:
        records.count { true } == 2
        records[0] == [age: "30", name: "Alice"]
    }

    void "test decode empty csv with default decoder"() throws CsvException {
        when:
        def records = defaultDecoder.decode("", DEFAULT_CONFIG)

        then:
        records.count { true } == 0
    }

    void "test decode csv with special characters using default decoder"() throws CsvException {
        when:
        def records = defaultDecoder.decode('name,age\nAlice ""The Great"",30\nBob,25', DEFAULT_CONFIG)

        then:
        records.count { true } == 2
        records[0] == [name: 'Alice "The Great"', age: "30"]
        records[1] == [name: "Bob", age: "25"]
    }

    void "test decode headerless csv with no header decoder"() throws CsvException {
        when:
        def records = noHeaderDecoder.decode("Alice,30\nBob,25", DEFAULT_CONFIG)

        then:
        records.count { true } == 2
        records[0] == ["Alice", "30"]
        records[1] == ["Bob", "25"]
    }

    void "test decode headerless csv with empty values"() throws CsvException {
        when:
        def records = noHeaderDecoder.decode("Alice,\n,Bob", DEFAULT_CONFIG)

        then:
        records.count { true } == 2
        records[0] == ["Alice", null]
        records[1] == [null, "Bob"]
    }

    void "test decode inline csv with inline decoder"() throws CsvException {
        when:
        def records = inlineDecoder.decode("Alice|30|Bob|25", DEFAULT_CONFIG)

        then:
        records.count { true } == 4
        records[0] == ["Alice"]
        records[1] == ["30"]
        records[2] == ["Bob"]
        records[3] == ["25"]
    }

    void "test decode inline csv with quoted values"() throws CsvException {
        when:
        def records = inlineDecoder.decode('"Alice, Jr."|30|Bob|25', DEFAULT_CONFIG)

        then:
        records.count { true } == 4
        records[0] == ["Alice", " Jr."]
        records[1] == ["30"]
        records[2] == ["Bob"]
        records[3] == ["25"]
    }
}