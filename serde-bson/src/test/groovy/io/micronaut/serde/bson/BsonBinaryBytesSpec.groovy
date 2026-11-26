package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class BsonBinaryBytesSpec extends Specification {

    @Inject
    BsonBinaryMapper bsonBinaryMapper

    def "writeValueAsBytes writes the same document as the stream API"() {
        given:
        def room = new Room(name: name, activeState: new PublicRoomState(label: "open", inviteCode: "abc"))

        when:
        def untyped = bsonBinaryMapper.writeValueAsBytes(room)
        def typed = bsonBinaryMapper.writeValueAsBytes(Argument.of(Room), room)
        def untypedStream = new ByteArrayOutputStream()
        bsonBinaryMapper.writeValue(untypedStream, room)
        def typedStream = new ByteArrayOutputStream()
        bsonBinaryMapper.writeValue(typedStream, Argument.of(Room), room)

        then:
        untyped == untypedStream.toByteArray()
        typed == typedStream.toByteArray()
        bsonBinaryMapper.readValue(typed, Argument.of(Room)).name == name

        where: "a document larger than the initial buffer is covered too"
        name << ["demo", "x" * 5000]
    }
}
