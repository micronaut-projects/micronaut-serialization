package io.micronaut.serde.csv.apache.commons

import spock.lang.Specification

class CsvFormatsSpec extends Specification {

    void "test default format has expected settings"() {
        expect:
        def format = CsvFormats.DEFAULT
        format.getIgnoreEmptyLines()
        format.getCommentMarker() == '#'
        format.getNullString() == ""
        format.getSkipHeaderRecord()
    }
}
