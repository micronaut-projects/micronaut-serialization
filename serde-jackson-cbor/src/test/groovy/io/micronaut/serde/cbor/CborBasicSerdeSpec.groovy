package io.micronaut.serde.cbor

import io.micronaut.context.annotation.Property
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.serde.jackson.JacksonJsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * Runs the shared basic serde TCK against CBOR by bridging JSON fixtures through {@link JsonNode}.
 */
@Property(name = "micronaut.serde.deserialization.fail-on-null-for-primitives", value = "false")
@MicronautTest
class CborBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    CborObjectMapper cborMapper

    @Inject
    JacksonJsonMapper jacksonMapper

    @Override
    JsonMapper getJsonMapper() {
        return cborMapper
    }

    @Override
    String writeJson(JsonMapper jsonMapper, Object bean) {
        return renderAsJson(cborMapper.writeValueAsBytes(bean))
    }

    @Override
    String writeJson(JsonMapper jsonMapper, Argument argument, Object bean) {
        return renderAsJson(cborMapper.writeValueAsBytes(argument, bean))
    }

    /** Round-trips through real CBOR bytes so the assertions cover the wire format, not just the tree encoder. */
    private String renderAsJson(byte[] cbor) {
        return jacksonMapper.writeValueAsString(cborMapper.readValue(cbor, Argument.of(JsonNode)))
    }

    @Override
    byte[] jsonAsBytes(String json) {
        JsonNode tree = jacksonMapper.readValue(json, JsonNode)
        return cborMapper.writeValueAsBytes(Argument.of(JsonNode), tree)
    }

    @Override
    boolean jsonMatches(String result, String expected) {
        JsonNode actual = jacksonMapper.readValue(result, JsonNode)
        JsonNode expect = jacksonMapper.readValue(expected, JsonNode)
        return actual == expect
    }

    @Override
    boolean objRepresentationMatches(Object obj, String json) {
        def reread = cborMapper.readValue(jsonAsBytes(json), Argument.of(obj.getClass()))
        assert reread == obj
        return true
    }
}
