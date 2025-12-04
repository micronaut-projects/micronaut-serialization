package io.micronaut.serde.jackson

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class JacksonJsonNodeSerdeSpec extends Specification {
    @Inject
    ObjectMapper objectMapper

    def 'back and forth'(JsonNode node) {
        when:
        def json = objectMapper.writeValueAsString(node)
        def back = objectMapper.readValue(json, JsonNode)
        then:
        back == node

        where:
        // commented nodes have ambiguous encoding with other nodes
        node << [
                JsonNodeFactory.instance.nullNode(),
                JsonNodeFactory.instance.booleanNode(true),
                JsonNodeFactory.instance.booleanNode(false),
                JsonNodeFactory.instance.textNode("foo"),
                JsonNodeFactory.instance.numberNode((byte) 1),
                //JsonNodeFactory.instance.numberNode((short) 1),
                JsonNodeFactory.instance.numberNode((int) 1),
                JsonNodeFactory.instance.numberNode(Long.MAX_VALUE),
                //JsonNodeFactory.instance.numberNode((float) 1),
                JsonNodeFactory.instance.numberNode((double) 1),
                JsonNodeFactory.instance.numberNode(BigInteger.valueOf(Long.MAX_VALUE) + 1),
                //JsonNodeFactory.instance.numberNode(BigDecimal.valueOf(Double.MAX_VALUE) + 1.5),
                //JsonNodeFactory.instance.binaryNode("foo".bytes),
                JsonNodeFactory.instance.objectNode()
                        .<ObjectNode> set("p1", JsonNodeFactory.instance.numberNode(1))
                        .<ObjectNode> set("p2", JsonNodeFactory.instance.numberNode(2)),
                JsonNodeFactory.instance.arrayNode().add(1).add(2),
        ]
    }
}
