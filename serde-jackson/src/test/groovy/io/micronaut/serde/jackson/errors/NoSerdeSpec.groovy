package io.micronaut.serde.jackson.errors

import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class NoSerdeSpec extends Specification {
    @Inject ObjectMapper objectMapper

    void "test error when no serde is present"() {
        when:
        objectMapper.writeValueAsString(new Foo())

        then:
        def e = thrown(SerdeException)
        e.message == 'No serializable introspection present for type Foo. Consider adding Serdeable. Serializable annotate to type Foo. Alternatively if you are not in control of the project\'s source code, you can use @SerdeImport(Foo.class) to enable serialization of this type.'
    }

    void "test NPE"() {
        when:
        objectMapper.readValue("{}", WithNpe)

        then:
        def e = thrown(SerdeException)
        e.message == 'Error deserializing type: WithNpe'
    }

    void "test NPE 2"() {
        when:
        objectMapper.updateValueFromTree(new WithNpe("noNPE"), JsonNode.from(Map.of()))

        then:
        def e = thrown(SerdeException)
        e.message == 'Error deserializing value: WithNpeToString of type: WithNpe'
    }

    void "test NPE 3"() {
        when:
        objectMapper.writeValueAsString(new WithNpe("noNPE"))

        then:
        def e = thrown(SerdeException)
        e.message == 'Error serializing value at path: '
    }

    static class Foo {}

}


