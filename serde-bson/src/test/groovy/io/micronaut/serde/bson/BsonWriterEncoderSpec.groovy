package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.serde.Keys
import io.micronaut.serde.KeysAwareEncoder
import io.micronaut.serde.LimitingStream
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import spock.lang.Specification

class BsonWriterEncoderSpec extends Specification {
    def 'currentPath'() {
        given:
        def encoder = new BsonWriterEncoder(new BsonDocumentWriter(new BsonDocument()), LimitingStream.DEFAULT_LIMITS)

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

    def 'keys aware encoder writes indexed keys'() {
        given:
        def document = new BsonDocument()
        def encoder = new BsonWriterEncoder(new BsonDocumentWriter(document), LimitingStream.DEFAULT_LIMITS)
        def keys = Keys.create('foo', 'bar')

        when:
        def object = (KeysAwareEncoder) encoder.encodeObject(Argument.VOID)
        object.encodeKey(keys, 1)
        object.encodeString('baz')
        object.finishStructure()

        then:
        document.getString('bar').value == 'baz'
    }
}
