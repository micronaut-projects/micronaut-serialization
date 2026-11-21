package io.micronaut.serde.toon.util

import io.micronaut.serde.LimitingStream
import spock.lang.Specification

import java.util.regex.Pattern

/**
 * The hand-written number matching, line splitting and double formatting give the results of the
 * regular expressions and of the decimal formatting they replace.
 */
class ToonScannerParitySpec extends Specification {

    private static final Pattern NUMERIC_LITERAL = Pattern.compile('^[+-]?[0-9]+(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?$', Pattern.CASE_INSENSITIVE)
    private static final Pattern DECODE_NUMBER = Pattern.compile('^-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?$', Pattern.CASE_INSENSITIVE)

    void "numbers are matched as by the grammar"() {
        given:
        def tokens = ['', '0', '-0', '+0', '05', '-05', '0.5', '.5', '5.', '1e5', '1E5', '1e+5', '1e-5', '1e', '1e+',
                      '-', '+', '1.2.3', '12a', 'a12', '1_000', '00', '0e1', '-0.0e-0', '+12.50E+07', '١٢', '1 ', ' 1',
                      '1\n', '123456789012345678901234567890', '-1.5e10', '--1', '+-1', '0x10', 'NaN', 'Infinity']
        def random = new Random(42)
        def alphabet = '0123456789+-.eE x'
        1000.times {
            def sb = new StringBuilder()
            (random.nextInt(7) + 1).times { sb.append(alphabet.charAt(random.nextInt(alphabet.length()))) }
            tokens << sb.toString()
        }

        expect:
        tokens.every { token ->
            assert ToonEscapes.matchesNumber(token, false) == DECODE_NUMBER.matcher(token).matches(): token
            assert ToonEscapes.matchesNumber(token, true) == NUMERIC_LITERAL.matcher(token).matches(): token
            assert ToonEscapes.isNumberToken(token) == DECODE_NUMBER.matcher(token).matches(): token
            true
        }
    }

    void "doubles are formatted as by the decimal form"() {
        given:
        def canonical = ToonNumbers.getDeclaredMethod('canonicalDecimal', BigDecimal)
        canonical.accessible = true
        def values = [0d, -0d, 1d, -1d, 1.5d, 10d, 100d, 0.001d, 0.0001d, 1e-6d, 1e-7d, 9999999d, 1e7d, 1.0E10d,
                      123.456d, -0.5d, Double.MIN_VALUE, Double.MAX_VALUE, 1e21d, 1e20d, 0.1d + 0.2d, 1d / 3d]
        def random = new Random(7)
        2000.times {
            values << random.nextDouble() * Math.pow(10, random.nextInt(30) - 10) * (random.nextBoolean() ? 1 : -1)
        }

        expect:
        values.every { double d ->
            assert ToonNumbers.format(d) == canonical.invoke(null, new BigDecimal(Double.toString(d))): d
            true
        }
    }

    void "lines are split at every line terminator"() {
        given:
        def text = 'a: 1\r\nb: 2\rc: 3\n\nd: 4\r\n'

        when:
        def parser = new ToonDocumentParser(text, LimitingStream.DEFAULT_LIMITS)
        def lines = parser.@lines

        then:
        lines*.lineNumber() == [1, 2, 3, 5]
        lines*.content() == ['a: 1', 'b: 2', 'c: 3', 'd: 4']
        lines*.precededByBlankLine() == [false, false, false, true]
    }
}
