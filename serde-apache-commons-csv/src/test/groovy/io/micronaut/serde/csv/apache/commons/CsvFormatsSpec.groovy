package io.micronaut.serde.csv.apache.commons


import org.apache.commons.csv.QuoteMode
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

    void "test no header format has no header"() {
        expect:
        def format = CsvFormats.NO_HEADER
        format.getHeader() == null
        format.getQuoteMode() == QuoteMode.MINIMAL
        format.getNullString() == ""
    }
}
