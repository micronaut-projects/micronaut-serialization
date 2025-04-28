package io.micronaut.serde.support.deserializers

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.support.deserializers.buffer.BufferedDecoder
import io.micronaut.serde.support.util.JsonNodeDecoder
import org.intellij.lang.annotations.Language
import spock.lang.Specification

class BufferedDecoderSpec extends Specification {
    def 'simple'() {
        given:
        def ctx = ApplicationContext.run()
        def outerDecoder = createDecoder(ctx, """{"a": 1, "b": 2, "c": 3}""")

        def primed = BufferedDecoder.of(outerDecoder)

        when:
        def buffered1 = primed.decodeObject()

        then:
        buffered1.decodeKey() == "a"
        buffered1.decodeInt() == 1
        buffered1.decodeKey() == "b"
        buffered1.skipValue()
        buffered1.decodeKey() == "c"
        buffered1.decodeInt() == 3
        buffered1.decodeKey() == null
        buffered1.finishStructure()

        when:
        def buffered2 = primed.decodeObject()

        then:
        buffered2.decodeKey() == "b"
        buffered2.decodeInt() == 2
        buffered2.finishStructure(true)

        cleanup:
        ctx.close()
    }

    def 'simple structures'() {
        given:
        def ctx = ApplicationContext.run()
        def outerDecoder = createDecoder(ctx, """{"a": [1], "b": {"foo": "bar"}, "c": {"fizz": "buzz"}}""")

        def buffered = BufferedDecoder.of(outerDecoder)

        when:
        def buffered1 = buffered.decodeObject()

        then:
        buffered1.decodeKey() == "a"
        def arr1 = buffered1.decodeArray()
        arr1.decodeInt() == 1
        arr1.finishStructure()
        buffered1.decodeKey() == "b"
        buffered1.decodeNull() == false
        buffered1.skipValue()
        buffered1.decodeKey() == "c"
        def obj3 = buffered1.decodeObject()
        obj3.decodeKey() == "fizz"
        obj3.decodeString() == "buzz"
        obj3.finishStructure()
        buffered1.decodeKey() == null
        buffered1.finishStructure()

        when:
        def buffered2 = buffered.decodeObject()

        then:
        buffered2.decodeKey() == "b"
        def obj2 = buffered2.decodeObject()
        obj2.decodeKey() == "foo"
        obj2.decodeString() == "bar"
        obj2.finishStructure()
        buffered2.finishStructure(true)

        cleanup:
        ctx.close()
    }

    def 'simple structures with null'() {
        given:
            def ctx = ApplicationContext.run()
            def outerDecoder = createDecoder(ctx, """{"a": [1], "b": null, "c": {"fizz": "buzz"}}""")

            def primed = BufferedDecoder.of(outerDecoder)

        when:
            def demux1 = primed.decodeObject()

        then:
            demux1.decodeKey() == "a"
            def arr1 = demux1.decodeArray()
            arr1.decodeInt() == 1
            arr1.finishStructure()
            demux1.decodeKey() == "b"
            demux1.skipValue()
            demux1.finishStructure(true)

        when:
            def demux2 = primed.decodeObject()
        then:
            demux2.decodeKey() == "b"
            demux2.decodeNull()
            demux2.decodeKey() == "c"
            def obj3 = demux2.decodeObject()
            obj3.decodeKey() == "fizz"
            obj3.decodeString() == "buzz"
            obj3.finishStructure()
            demux2.decodeKey() == null
            demux2.finishStructure()

        cleanup:
            ctx.close()
    }

    def 'simple structures with null not consuming'() {
        given:
            def ctx = ApplicationContext.run()
            def outerDecoder = createDecoder(ctx, """{"a": [1], "b": null, "c": {"fizz": "buzz"}}""")

            def buffered = BufferedDecoder.of(outerDecoder)

        when:
            def buffered1 = buffered.decodeObjectNonConsuming()
        then:
            buffered1.decodeKey() == "a"
            def arr1 = buffered1.decodeArray()
            arr1.decodeInt() == 1
            arr1.finishStructure()
            buffered1.decodeKey() == "b"
            buffered1.decodeNull()
            buffered1.finishStructure(true)

        when:
            def buffered2 = buffered.decodeObject()
        then:
            buffered2.decodeKey() == "a"
            buffered2.skipValue()
            buffered2.decodeKey() == "b"
            buffered2.decodeNull()
            buffered2.decodeKey() == "c"
            def obj3 = buffered2.decodeObject()
            obj3.decodeKey() == "fizz"
            obj3.decodeString() == "buzz"
            obj3.finishStructure()
            buffered2.decodeKey() == null
            buffered2.finishStructure()

        cleanup:
            ctx.close()
    }

    private static Decoder createDecoder(ApplicationContext ctx, @Language("json") String json) {
        JsonNodeDecoder.create(ctx.getBean(JsonMapper).readValue(json, JsonNode), LimitingStream.DEFAULT_LIMITS)
    }
}
