package io.micronaut.serde.support

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class MapKeySerdeSpec extends Specification {
    @Inject ObjectMapper jsonMapper

    void "keys of #keyType.simpleName are written as their string form and read back"() {
        given:
        def type = Argument.mapOf(Argument.of(keyType), Argument.STRING)

        when:
        def json = jsonMapper.writeValueAsString(type, map)

        then:
        json == expected

        when:
        def read = jsonMapper.readValue(json, type)

        then:
        read == map
        read.keySet().every { keyType.isInstance(it) }

        where:
        keyType | map                                                                    | expected
        Integer | [(1): "a", (-20): "b"]                                                 | '{"1":"a","-20":"b"}'
        Long    | [(1L): "a", (Long.MAX_VALUE): "b"]                                      | '{"1":"a","9223372036854775807":"b"}'
        Short   | [((short) 7): "a"]                                                     | '{"7":"a"}'
        Byte    | [((byte) 7): "a"]                                                      | '{"7":"a"}'
        Boolean | [(true): "a", (false): "b"]                                            | '{"true":"a","false":"b"}'
        UUID    | [(UUID.fromString("5a1c8f6e-0b1d-4c3e-9f7a-2d4b6c8e0a12")): "a"]       | '{"5a1c8f6e-0b1d-4c3e-9f7a-2d4b6c8e0a12":"a"}'
        Double  | [(1.5d): "a"]                                                          | '{"1.5":"a"}'
    }

    void "a key that is not a valid #keyType.simpleName is rejected"() {
        when:
        jsonMapper.readValue(json, Argument.mapOf(Argument.of(keyType), Argument.STRING))

        then:
        def e = thrown(SerdeException)
        e.message.contains("Error converting Map key")

        where:
        keyType | json
        Integer | '{"x":"a"}'
        Integer | '{"":"a"}'
        Long    | '{"1.5":"a"}'
        UUID    | '{"nope":"a"}'
    }

    void "a map with untyped keys still writes numeric keys"() {
        expect:
        jsonMapper.writeValueAsString([(1): "a", (2L): "b"]) == '{"1":"a","2":"b"}'
    }
}
