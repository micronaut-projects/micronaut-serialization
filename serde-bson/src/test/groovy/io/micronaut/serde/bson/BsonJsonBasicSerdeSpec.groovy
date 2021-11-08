package io.micronaut.serde.bson

import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonDocument

import java.nio.charset.StandardCharsets

@MicronautTest
class BsonJsonBasicSerdeSpec extends AbstractBasicSerdeSpec implements BsonJsonSpec {

    @Inject
    BsonJsonMapper jsonMapper

    @Override
    BsonJsonMapper getBsonJsonMapper() {
        return jsonMapper
    }

    @Override
    boolean jsonMatches(String result, String expected) {
        return BsonDocument.parse(expected).toJson() == result
    }

    @Override
    boolean objRepresentationMatches(Object obj, String json) {
        def result = BsonDocument.parse(json).toJson()
        def expected = new String(jsonMapper.writeValueAsBytes(obj), StandardCharsets.UTF_8)
        assert result == expected
        return result == expected
    }
}
