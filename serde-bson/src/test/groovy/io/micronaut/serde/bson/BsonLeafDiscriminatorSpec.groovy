package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Reproduces / guards micronaut-projects/micronaut-core#12853:
 * leaf classes that carry {@code @BsonDiscriminator} (required by the MongoDB POJO
 * convention so the base can discover them) must still deserialize when the
 * document is correct under the MN5 default
 * {@code micronaut.serde.deserialization.subtypes-require-default-impl=true}.
 *
 * Unlike {@link BsonMappingSpec}, this suite deliberately does <strong>not</strong>
 * relax that property — a global bypass is not an acceptable fix.
 */
@MicronautTest
class BsonLeafDiscriminatorSpec extends Specification implements BsonJsonSpec, BsonBinarySpec {

    @Inject
    BsonBinaryMapper bsonBinaryMapper

    @Inject
    BsonJsonMapper bsonJsonMapper

    def "leaf @BsonDiscriminator hierarchy round-trips under strict subtypes default"() {
        given:
        def privateState = new PrivateRoomState(label: "kickoff", ownerId: "user-42")
        def publicState = new PublicRoomState(label: "open", inviteCode: "abc")
        def privateRoom = new Room(name: "demo-private", activeState: privateState)
        def publicRoom = new Room(name: "demo-public", activeState: publicState)

        when:
        def privateBytes = bsonBinaryMapper.writeValueAsBytes(privateRoom)
        def publicBytes = bsonBinaryMapper.writeValueAsBytes(publicRoom)
        def rereadPrivate = bsonBinaryMapper.readValue(privateBytes, Argument.of(Room))
        def rereadPublic = bsonBinaryMapper.readValue(publicBytes, Argument.of(Room))

        then:
        rereadPrivate.name == "demo-private"
        rereadPrivate.activeState instanceof PrivateRoomState
        (rereadPrivate.activeState as PrivateRoomState).ownerId == "user-42"
        rereadPrivate.activeState.label == "kickoff"

        rereadPublic.name == "demo-public"
        rereadPublic.activeState instanceof PublicRoomState
        (rereadPublic.activeState as PublicRoomState).inviteCode == "abc"
        rereadPublic.activeState.label == "open"
    }

    def "leaf @BsonDiscriminator hierarchy deserializes from bson-json under strict subtypes default"() {
        given:
        def privateJson = '''{"name":"demo-private","activeState":{"_t":"private_room_state","label":"kickoff","ownerId":"user-42"}}'''
        def publicJson = '''{"name":"demo-public","activeState":{"_t":"public_room_state","label":"open","inviteCode":"abc"}}'''

        when:
        def rereadPrivate = bsonJsonMapper.readValue(privateJson.getBytes(StandardCharsets.UTF_8), Argument.of(Room))
        def rereadPublic = bsonJsonMapper.readValue(publicJson.getBytes(StandardCharsets.UTF_8), Argument.of(Room))

        then:
        rereadPrivate.activeState instanceof PrivateRoomState
        (rereadPrivate.activeState as PrivateRoomState).ownerId == "user-42"
        rereadPublic.activeState instanceof PublicRoomState
        (rereadPublic.activeState as PublicRoomState).inviteCode == "abc"
    }

    def "reading leaf subtype directly still works under strict subtypes default"() {
        given:
        def state = new PrivateRoomState(label: "solo", ownerId: "user-7")

        when:
        def bytes = bsonBinaryMapper.writeValueAsBytes(state)
        def reread = bsonBinaryMapper.readValue(bytes, Argument.of(PrivateRoomState))

        then:
        reread instanceof PrivateRoomState
        reread.ownerId == "user-7"
        reread.label == "solo"
    }

    def "unknown discriminator on base type still fails under strict subtypes default"() {
        given:
        def json = '''{"name":"broken","activeState":{"_t":"does_not_exist","label":"x"}}'''

        when:
        bsonJsonMapper.readValue(json.getBytes(StandardCharsets.UTF_8), Argument.of(Room))

        then:
        def e = thrown(Exception)
        e.message?.contains("Could not resolve subtype") || e.cause?.message?.contains("Could not resolve subtype")
    }
}
