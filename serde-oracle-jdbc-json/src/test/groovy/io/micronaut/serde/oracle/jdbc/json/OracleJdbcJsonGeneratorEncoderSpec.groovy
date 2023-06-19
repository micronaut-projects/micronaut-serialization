package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import oracle.sql.json.OracleJsonFactory
import spock.lang.Specification

class OracleJdbcJsonGeneratorEncoderSpec extends Specification {
    def 'currentPath'() {
        given:
        def encoder = new OracleJdbcJsonGeneratorEncoder(new OracleJsonFactory().createJsonBinaryGenerator(new ByteArrayOutputStream()), LimitingStream.DEFAULT_LIMITS)

        when:
        def outer = encoder.encodeObject(Argument.VOID)
        outer.encodeKey('foo')
        outer.encodeString('bar')
        then:
        outer.currentPath() == '->foo'

        when:
        outer.encodeKey('')
        outer.encodeString('bar')
        then:
        outer.currentPath() == '->'

        when:
        outer.encodeKey('baz')
        def array = outer.encodeArray(Argument.VOID)
        array.encodeString('foo')
        then:
        array.currentPath() == '->baz->1'
    }
}
