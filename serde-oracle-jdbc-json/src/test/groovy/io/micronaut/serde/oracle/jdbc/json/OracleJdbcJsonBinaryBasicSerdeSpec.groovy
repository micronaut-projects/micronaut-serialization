package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import oracle.sql.json.OracleJsonFactory

import java.nio.charset.StandardCharsets

@MicronautTest
class OracleJdbcJsonBinaryBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    OracleJdbcJsonBinaryObjectMapper jsonMapper

    @Inject
    OracleJdbcJsonTextObjectMapper textObjectMapper

    @Override
    String writeJson(JsonMapper jsonMapper, Object bean) {
        def factory = new OracleJsonFactory()
        def bytes = jsonMapper.writeValueAsBytes(bean)

        def parser = factory.createJsonBinaryParser(new ByteArrayInputStream(bytes))
        parser.next()
        def object = parser.getObject()
        def stream = new ByteArrayOutputStream()
        factory.createJsonTextGenerator(stream).write(object).close()
        return new String(stream.toByteArray(), StandardCharsets.UTF_8)
    }

    @Override
    byte[] jsonAsBytes(String json) {
        def factory = new OracleJsonFactory()
        def parser = factory.createJsonTextParser(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
        parser.next()
        def object = parser.getObject()
        def stream = new ByteArrayOutputStream()
        new OracleJsonFactory().createJsonBinaryGenerator(stream).write(object).close()
        return stream.toByteArray()
    }

    boolean objRepresentationMatches(Object obj, String json) {
        def expected = textObjectMapper.readValue(json, Argument.of(obj.getClass()))
        assert obj == expected
        obj == expected
    }
}
