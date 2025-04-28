package io.micronaut.serde.jmespath

import com.fasterxml.jackson.core.JsonFactoryBuilder
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.jackson.JacksonDecoder
import io.micronaut.serde.jackson.JacksonEncoder
import io.micronaut.serde.support.util.JsonNodeToStringUtil
import spock.lang.Specification

class DecodeSpec extends Specification {

    private static Decoder createDecoder(String json) { // language=json
        return JacksonDecoder.create(new JsonFactoryBuilder().build().createParser(json), LimitingStream.DEFAULT_LIMITS)
    }

    private static String toJson(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()
        def generator = new JsonFactoryBuilder().build().createGenerator(byteArrayOutputStream)
        def encoder = JacksonEncoder.create(generator, LimitingStream.DEFAULT_LIMITS)
        JsonNodeToStringUtil.encode(encoder, jsonNode)
        generator.close()
        return new String(byteArrayOutputStream.toByteArray())
    }

    def testKeySelection() {
        when:
            def decoder = createDecoder("""
{ "foo": {"bar": {"hello": "world"}}}
""")
            def node = SerdeJmesPathDecoder.decode(decoder, path)
        then:
            toJson(node) == result

        where:
            path                || result
            "foo.bar"           || """{"hello":"world"}"""
            "foo.bar.hello"     || '"world"'
            "xyz.bar"           || null
            "foo.bar.hello.xxx" || null
    }

    def testArraySelection() {
        when:
            def decoder = createDecoder("""
{ "foo": [{"bar": {"hello": "world"}}, {"abc": 123}]}
""")
            def node = SerdeJmesPathDecoder.decode(decoder, path)
        then:
            toJson(node) == result

        where:
            path      || result
            "foo[0]"  || """{"bar":{"hello":"world"}}"""
            "foo[1]"  || """{"abc":123}"""
            "foo[2]"  || null
            "foo[-2]" || null
    }

}
