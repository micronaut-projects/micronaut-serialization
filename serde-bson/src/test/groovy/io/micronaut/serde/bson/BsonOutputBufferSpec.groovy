package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonBinaryWriter
import org.bson.BsonSerializationException
import org.bson.io.BasicOutputBuffer
import org.bson.io.OutputBuffer
import spock.lang.Specification

@MicronautTest
class BsonOutputBufferSpec extends Specification {

    @Inject
    BsonBinaryMapper bsonBinaryMapper

    void "writes the same bytes as the BSON driver buffer for #description"() {
        expect:
        bytes(new BsonOutputBuffer(4), value) == bytes(new BasicOutputBuffer(), value)

        where:
        description            | value
        "ASCII"                | "hello"
        "an empty string"      | ""
        "Latin-1"              | "café"
        "CJK"                  | "日本語"
        "a surrogate pair"     | "a😀b"
        "an unpaired surrogate"| "a\ud800b"
        "a null character"     | "a\u0000b"
        "a long string"        | "x" * 5000
    }

    void "rejects a null character in a key"() {
        given:
        def buffer = new BsonOutputBuffer()
        buffer.writeInt32(7)

        when:
        buffer.writeCString("a\u0000b")

        then:
        thrown(BsonSerializationException)
        buffer.position == 4
    }

    void "writes integers at a position and truncates"() {
        given:
        def buffer = new BsonOutputBuffer(2)
        def basic = new BasicOutputBuffer()

        when:
        [buffer, basic].each {
            it.writeInt32(1)
            it.writeInt64(Long.MIN_VALUE)
            it.writeDouble(1.5d)
            it.writeInt32(0, 42)
            it.writeByte(7)
            it.truncateToPosition(it.position - 1)
        }

        then:
        buffer.toByteArray() == basic.toByteArray()
        buffer.byteBuffers[0].remaining() == basic.size
    }

    void "writeValueAsBytes writes the same document as the stream API"() {
        given:
        def users = [name: 'caf\u00e9', count: 3, friends: (1..200).collect { [id: it, name: "friend $it".toString(), tags: ['a', '\u65e5']] }]
        def type = Argument.mapOf(String, Object)

        when:
        def stream = new ByteArrayOutputStream()
        bsonBinaryMapper.writeValue(stream, type, users)
        def first = bsonBinaryMapper.writeValueAsBytes(type, users)
        def second = bsonBinaryMapper.writeValueAsBytes(type, users)

        then:
        first == stream.toByteArray()
        second == first
        bsonBinaryMapper.readValue(first, type) == users
    }

    private static byte[] bytes(OutputBuffer buffer, String value) {
        new BsonBinaryWriter(buffer).withCloseable { writer ->
            writer.writeStartDocument()
            writer.writeString("s", value)
            writer.writeName("k")
            writer.writeStartDocument()
            writer.writeEndDocument()
            writer.writeEndDocument()
        }
        return buffer.toByteArray()
    }
}
