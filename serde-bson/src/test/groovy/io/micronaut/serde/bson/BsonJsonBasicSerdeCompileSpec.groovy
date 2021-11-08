package io.micronaut.serde.bson

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeCompileSpec

class BsonJsonBasicSerdeCompileSpec extends AbstractBasicSerdeCompileSpec implements BsonJsonSpec {

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        return BsonJsonMapper
    }

    @Override
    BsonJsonMapper getBsonJsonMapper() {
        return jsonMapper
    }

}
