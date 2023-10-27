package io.micronaut.serde.support.deserializers

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.support.util.JsonNodeDecoder
import org.intellij.lang.annotations.Language
import spock.lang.Specification

class DemuxingObjectDecoderSpec extends Specification {
    def 'simple'() {
        given:
        def ctx = ApplicationContext.run()
        def outerDecoder = createDecoder(ctx, """{"a": 1, "b": 2, "c": 3}""")

        def primed = DemuxingObjectDecoder.prime(outerDecoder)
        def demux1 = primed.decodeObject()
        def demux2 = primed.decodeObject()

        expect:
        demux1.decodeKey() == "a"
        demux1.decodeInt() == 1
        demux1.decodeKey() == "b"
        demux1.skipValue()
        demux1.decodeKey() == "c"
        demux1.decodeInt() == 3
        demux1.decodeKey() == null
        demux1.finishStructure()

        demux2.decodeKey() == "b"
        demux2.decodeInt() == 2
        demux2.finishStructure()

        cleanup:
        ctx.close()
    }

    def 'simple structures'() {
        given:
        def ctx = ApplicationContext.run()
        def outerDecoder = createDecoder(ctx, """{"a": [1], "b": {"foo": "bar"}, "c": {"fizz": "buzz"}}""")

        def primed = DemuxingObjectDecoder.prime(outerDecoder)
        def demux1 = primed.decodeObject()
        def demux2 = primed.decodeObject()

        expect:
        demux1.decodeKey() == "a"
        def arr1 = demux1.decodeArray()
        arr1.decodeInt() == 1
        arr1.finishStructure()
        demux1.decodeKey() == "b"
        demux1.skipValue()
        demux1.decodeKey() == "c"
        def obj3 = demux1.decodeObject()
        obj3.decodeKey() == "fizz"
        obj3.decodeString() == "buzz"
        obj3.finishStructure()
        demux1.decodeKey() == null
        demux1.finishStructure()

        demux2.decodeKey() == "b"
        def obj2 = demux2.decodeObject()
        obj2.decodeKey() == "foo"
        obj2.decodeString() == "bar"
        obj2.finishStructure()
        demux2.finishStructure()

        cleanup:
        ctx.close()
    }

    def 'interleaved'() {
        given:
        def ctx = ApplicationContext.run()
        def outerDecoder = createDecoder(ctx, """{"a": 1, "b": 2, "c": 3}""")

        def primed = DemuxingObjectDecoder.prime(outerDecoder)
        def demux1 = primed.decodeObject()
        def demux2 = primed.decodeObject()

        expect:
        demux1.decodeKey() == "a"
        demux1.decodeInt() == 1
        demux2.decodeKey() == "b"
        !demux2.decodeNull()
        demux2.decodeInt() == 2
        demux1.decodeKey() == "c"
        demux1.decodeInt() == 3
        demux1.decodeKey() == null
        demux1.finishStructure()
        demux2.decodeKey() == null
        demux2.finishStructure()

        cleanup:
        ctx.close()
    }

    private static Decoder createDecoder(ApplicationContext ctx, @Language("json") String json) {
        JsonNodeDecoder.create(ctx.getBean(JsonMapper).readValue(json, JsonNode), LimitingStream.DEFAULT_LIMITS)
    }
}
