package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import oracle.sql.json.OracleJsonObject

import java.nio.charset.StandardCharsets

@MicronautTest
class OracleJdbcJsonBinaryBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    OracleJdbcJsonBinaryObjectMapper osonMapper

    @Inject
    OracleJdbcJsonTextObjectMapper textJsonMapper

    @Override
    JsonMapper getJsonMapper() {
        return osonMapper
    }

    @Override
    String writeJson(JsonMapper jsonMapper, Object bean) {
        def bytes = jsonMapper.writeValueAsBytes(bean)
        def object = osonMapper.readValue(bytes, OracleJsonObject)
        return new String(textJsonMapper.writeValueAsBytes(object), StandardCharsets.UTF_8)
    }

    @Override
    byte[] jsonAsBytes(String json) {
        def object = textJsonMapper.readValue(json, OracleJsonObject)
        return osonMapper.writeValueAsBytes(object)
    }

    boolean objRepresentationMatches(Object obj, String json) {
        def expected = textJsonMapper.readValue(json, Argument.of(obj.getClass()))
        assert obj == expected
        obj == expected
    }
}
