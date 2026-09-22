package io.micronaut.serde.support

import io.micronaut.serde.LimitingStream
import spock.lang.Specification

class AbstractStreamDecoderSpec extends Specification {

    def 'getBigDecimalFromNumber uses the decimal representation of #number'() {
        given:
        AbstractStreamDecoder decoder = Spy(constructorArgs: [LimitingStream.DEFAULT_LIMITS])

        expect:
        decoder.getBigDecimalFromNumber(number) == expected

        where:
        number                   | expected
        0.1d                     | new BigDecimal('0.1')
        0.1f                     | new BigDecimal('0.1')
        new BigDecimal('1.25')   | new BigDecimal('1.25')
        BigInteger.TEN           | BigDecimal.TEN
        42L                      | BigDecimal.valueOf(42)
    }
}
