package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import spock.lang.Specification

class BsonWriterEncoderSpec extends Specification {
    def 'currentPath'() {
        given:
        def encoder = new BsonWriterEncoder(new BsonDocumentWriter(new BsonDocument()))

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
