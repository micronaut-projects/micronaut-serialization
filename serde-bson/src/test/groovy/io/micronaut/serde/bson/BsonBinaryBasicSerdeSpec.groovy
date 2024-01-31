package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
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
}
