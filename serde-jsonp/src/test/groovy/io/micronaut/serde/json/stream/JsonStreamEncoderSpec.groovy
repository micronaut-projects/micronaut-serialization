package io.micronaut.serde.json.stream

import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import jakarta.json.Json
import spock.lang.Specification

class JsonStreamEncoderSpec extends Specification {
    def 'currentPath'() {
        given:
        def encoder = new JsonStreamEncoder(Json.createGenerator(new ByteArrayOutputStream()), LimitingStream.DEFAULT_LIMITS)

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
