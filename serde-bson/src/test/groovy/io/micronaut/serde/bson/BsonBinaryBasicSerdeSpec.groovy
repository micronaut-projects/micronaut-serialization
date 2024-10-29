package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonDocument

@MicronautTest
class BsonBinaryBasicSerdeSpec extends AbstractBasicSerdeSpec implements BsonBinarySpec {

    @Inject
    BsonBinaryMapper jsonMapper

    @Override
    BsonBinaryMapper getBsonBinaryMapper() {
        return jsonMapper
    }

    @Override
    String writeJson(JsonMapper jsonMapper, Object bean) {
        return encodeAsBinaryDecodeJson(bean)
    }

    @Override
    String writeJson(JsonMapper jsonMapper, Argument argument, Object bean) {
        return encodeAsBinaryDecodeJson(argument, bean)
    }

    @Override
    byte[] jsonAsBytes(String json) {
        def parse = BsonDocument.parse(json)
        return writeToByteArray(parse)
    }

    @Override
    boolean jsonMatches(String result, String expected) {
        return BsonDocument.parse(expected).toJson() == result
    }

    @Override
    boolean objRepresentationMatches(Object obj, String json) {
        def result = encodeAsBinaryDecodeAsObject(BsonDocument.parse(json), obj.getClass())
        def expected = obj
        assert result == expected
        return result == expected
    }

    def "validate json node including type"() {
        when:
            def result = serializeDeserializeAs(
                JsonNode.createObjectNode(["v": jsonNode]), Argument.of(JsonNode.class)).get("v")
        then:
            result.value == jsonNode.value && result.value.class == jsonNode.value.class

        where:
            // the type doesn't match for float and big integer as bson encodes them as double and big decimal
            jsonNode << [
                JsonNode.createBooleanNode(true),
                JsonNode.createNumberNode(123),
                JsonNode.createNumberNode(234L),
                JsonNode.createNumberNode(123.234D),
                JsonNode.createNumberNode(BigDecimal.valueOf(12345.12345)),
                JsonNode.createStringNode("Hello"),
            ]
    }
}
