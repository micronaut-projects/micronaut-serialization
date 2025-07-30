package io.micronaut.serde.jackson

import com.fasterxml.jackson.core.JsonFactoryBuilder
import io.micronaut.json.tree.JsonArray
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.LookaheadDecoder
import io.micronaut.serde.support.deserializers.buffer.BufferedDecoder
import org.intellij.lang.annotations.Language
import spock.lang.Specification

class BufferedDecoderSpec extends Specification {
    def 'simple'() {
        given:
        def outerDecoder = createDecoder("""{"a": 1, "b": 2, "c": 3}""")

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
    }

    def 'simple lookup'() {
        given:
        def outerDecoder = createDecoder("""{"a": 1, "b": 2, "c": 3}""") as LookaheadDecoder

        when:
        def buffered = BufferedDecoder.of(outerDecoder)

        then:
        buffered.lookahead() == LookaheadDecoder.TokenType.START_OBJECT

        when:
        def bufferedObject1 = buffered.decodeObject()

        then:
        bufferedObject1.lookahead() == LookaheadDecoder.TokenType.KEY
        bufferedObject1.decodeKey() == "a"
        bufferedObject1.decodeInt() == 1
        bufferedObject1.decodeKey() == "b"
        bufferedObject1.skipValue()
        bufferedObject1.decodeKey() == "c"
        bufferedObject1.decodeInt() == 3
        bufferedObject1.decodeKey() == null
        bufferedObject1.finishStructure()
        buffered.lookahead() == LookaheadDecoder.TokenType.START_OBJECT

        when:
        def bufferedObject2 = buffered.decodeObject()

        then:
        bufferedObject2.lookahead() == LookaheadDecoder.TokenType.KEY
        bufferedObject2.decodeKey() == "b"
        bufferedObject2.decodeInt() == 2
        bufferedObject2.finishStructure(true)
    }

    def 'object lookup'() {
        given:
        def outerDecoder = createDecoder("""{"a": 1, "b": 2, "c": 3}""") as LookaheadDecoder

        when:
        def buffered = BufferedDecoder.of(outerDecoder)

        then:
        buffered.lookahead() == LookaheadDecoder.TokenType.START_OBJECT

        when:
        def bufferedObject1 = buffered.decodeObject()

        then:
        bufferedObject1.lookahead() == LookaheadDecoder.TokenType.KEY
        bufferedObject1.decodeKey() == "a"
        bufferedObject1.skipValue()
        bufferedObject1.finishStructure(true)

        buffered.lookahead() == LookaheadDecoder.TokenType.START_OBJECT

        when:
        def bufferedObject2 = buffered.decodeObject()

        then:
        bufferedObject2.lookahead() == LookaheadDecoder.TokenType.KEY
        bufferedObject2.decodeKey() == "a"
        bufferedObject2.skipValue()
        bufferedObject2.finishStructure(true)
    }

    def 'object nested lookup'() {
        given:
        def outerDecoder = createDecoder("""{"a": 1, "b": 2, "nested": {"foo" : "bar", "abc" : "xyz"}}""") as LookaheadDecoder

        when:
        def buffered = BufferedDecoder.of(outerDecoder, false)

        then:
        buffered.lookahead() == LookaheadDecoder.TokenType.START_OBJECT

        when:
        def bufferedObject1 = buffered.decodeObject()

        then:
        moveToKeyValue(bufferedObject1, "nested")
        def nestedDecoder1 = bufferedObject1.decodeObject()
        moveToKeyValue(nestedDecoder1, "abc")
        nestedDecoder1.lookahead() == LookaheadDecoder.TokenType.STRING
        nestedDecoder1.decodeString() == "xyz"
        bufferedObject1.finishStructure(true)

        buffered.lookahead() == LookaheadDecoder.TokenType.START_OBJECT

        when:
        def bufferedObject2 = buffered.decodeObject()

        then:
        moveToKeyValue(bufferedObject2, "nested")
        def nestedDecoder2 = bufferedObject2.decodeObject()
        moveToKeyValue(nestedDecoder2, "foo")
        nestedDecoder2.lookahead() == LookaheadDecoder.TokenType.STRING
        nestedDecoder2.decodeString() == "bar"
        bufferedObject2.finishStructure(true)
    }

    def 'array lookup'() {
        given:
        def outerDecoder = createDecoder("""[1, 2, 3]""") as LookaheadDecoder

        when:
        def buffered = BufferedDecoder.of(outerDecoder)

        then:
        buffered.lookahead() == LookaheadDecoder.TokenType.START_ARRAY

        when:
        def bufferedArray1 = buffered.decodeArray()

        then:
        bufferedArray1.lookahead() == LookaheadDecoder.TokenType.NUMBER
        bufferedArray1.decodeInt() == 1
        bufferedArray1.finishStructure(true)

        buffered.lookahead() == LookaheadDecoder.TokenType.START_ARRAY

        when:
        def bufferedArray2 = buffered.decodeArray()

        then:
        bufferedArray2.lookahead() == LookaheadDecoder.TokenType.NUMBER
        bufferedArray1.decodeInt() == 2
        bufferedArray2.finishStructure(true)
        buffered.lookahead() == LookaheadDecoder.TokenType.START_ARRAY
    }

    def 'reuse'() {
        given:
        def outerDecoder = createDecoder("""{"a": 1, "b": 2, "c": 3}""")

        def buffered = BufferedDecoder.of(outerDecoder, false)

        when:
        def bufferedObjectDecoder = buffered.decodeObject()

        then:
        bufferedObjectDecoder.decodeKey() == "a"
        bufferedObjectDecoder.decodeInt() == 1
        bufferedObjectDecoder.decodeKey() == "b"
        bufferedObjectDecoder.skipValue()
        bufferedObjectDecoder.decodeKey() == "c"
        bufferedObjectDecoder.decodeInt() == 3
        bufferedObjectDecoder.decodeKey() == null
        bufferedObjectDecoder.finishStructure()

//        and:
//        bufferedObjectDecoder.decodeKey() == "a"
//        bufferedObjectDecoder.decodeInt() == 1
//        bufferedObjectDecoder.decodeKey() == "b"
//        bufferedObjectDecoder.skipValue()
//        bufferedObjectDecoder.decodeKey() == "c"
//        bufferedObjectDecoder.decodeInt() == 3
//        bufferedObjectDecoder.decodeKey() == null
//        bufferedObjectDecoder.finishStructure()
    }

    def 'simple structures'() {
        given:
        def outerDecoder = createDecoder("""{"a": [1], "b": {"foo": "bar"}, "c": {"fizz": "buzz"}}""")

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
    }

    def 'simple structures with null'() {
        given:
            def outerDecoder = createDecoder("""{"a": [1], "b": null, "c": {"fizz": "buzz"}}""")

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
    }

    def 'simple structures with null not consuming'() {
        given:
            def outerDecoder = createDecoder("""{"a": [1], "b": null, "c": {"fizz": "buzz"}}""")

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
//            buffered2.decodeKey() == "b"
//            buffered2.decodeNull()
            buffered2.decodeKey() == "c"
            def obj3 = buffered2.decodeObject()
            obj3.decodeKey() == "fizz"
            obj3.decodeString() == "buzz"
            obj3.finishStructure()
            buffered2.decodeKey() == null
            buffered2.finishStructure()
    }

    def 'decode array node'() {
        given:
            def outerDecoder = createDecoder("""{
      "foo": {
        "bar": 1,
        "baz": [
          2,
          3,
          4
        ]
      }
    }""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
        when: "first read"
            def fooDecoder = nextRoot.decodeObject()
        then:
            fooDecoder.decodeKey() == "bar"
            fooDecoder.decodeNode().intValue == 1
            fooDecoder.finishStructure(true)
            fooDecoder.close()
        and: "second read"
            fooDecoder.decodeKey() == "bar"
            fooDecoder.skipValue()
            fooDecoder.decodeKey() == "baz"
        when:
            def node = fooDecoder.decodeNode()
        then:
            node instanceof JsonArray
            node.size() == 3
            fooDecoder.finishStructure(true)
            fooDecoder.close()
        and: "third read"
            fooDecoder.decodeKey() == "bar"
            fooDecoder.skipValue()
            fooDecoder.decodeKey() == "baz"
        when:
            def arrayDecoder = fooDecoder.decodeArray()
        then:
            arrayDecoder.decodeInt() == 2
            fooDecoder.finishStructure(true)
            fooDecoder.close()

//        and: "forth read"
//            fooDecoder.decodeKey() == "bar"
//            fooDecoder.skipValue()
//            fooDecoder.decodeKey() == "baz"
//        when:
//            arrayDecoder = fooDecoder.decodeArray()
//        then:
//            arrayDecoder.decodeInt() == 2
//            arrayDecoder.decodeInt() == 3
//            arrayDecoder.decodeInt() == 4
//            fooDecoder.close()

    }

    def 'decode object node'() {
        given:
            def outerDecoder = createDecoder("""{
      "foo": {
        "bar": 1,
        "baz": {
          "foo": "bar",
          "abc": 123
        }
      }
    }""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
        when: "first read"
            def fooDecoder = nextRoot.decodeObject()
        then:
            fooDecoder.decodeKey() == "bar"
            fooDecoder.decodeNode().intValue == 1
            fooDecoder.finishStructure(true)
            fooDecoder.close()
        and: "second read"
            fooDecoder.decodeKey() == "bar"
            fooDecoder.skipValue()
            fooDecoder.decodeKey() == "baz"
        when:
            def node = fooDecoder.decodeNode()
        then:
            node instanceof JsonNode
            node.size() == 2
            fooDecoder.finishStructure(true)
            fooDecoder.close()
        and: "third read"
            fooDecoder.decodeKey() == "bar"
            fooDecoder.skipValue()
            fooDecoder.decodeKey() == "baz"
        when:
            def objDec = fooDecoder.decodeObject()
        then:
            objDec.decodeKey() == "foo"
            objDec.decodeString() == "bar"
            fooDecoder.finishStructure(true)
            fooDecoder.close()

        and: "forth read"
            fooDecoder.decodeKey() == "bar"
            fooDecoder.skipValue()
            fooDecoder.decodeKey() == "baz"
        when:
            objDec = fooDecoder.decodeObject()
        then:
            objDec.decodeKey() == "foo"
            objDec.skipValue()
            objDec.decodeKey() == "abc"
            objDec.decodeInt() == 123
            fooDecoder.finishStructure(false)
            fooDecoder.close()
    }

    def 'reset array decoder'() {
        given:
            def outerDecoder = createDecoder("""
{
  "foo": {"bar": ["zero", "one", "two"]}
}
""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
        when: "first read"
            def fooDecoder = nextRoot.decodeObject()
        then:
            fooDecoder.decodeKey() == "bar"
        when:
            def barDecoder = fooDecoder.decodeArray()
        then:
            barDecoder.hasNextArrayValue()
            barDecoder.skipValue()
            barDecoder.hasNextArrayValue()
            barDecoder.skipValue()
            barDecoder.hasNextArrayValue()
            barDecoder.skipValue()
            !barDecoder.hasNextArrayValue()
            barDecoder.finishStructure()
            barDecoder.close()
        and:
            barDecoder.hasNextArrayValue()
            barDecoder.skipValue()
            barDecoder.hasNextArrayValue()
            barDecoder.skipValue()
            barDecoder.hasNextArrayValue()
            barDecoder.skipValue()
            !barDecoder.hasNextArrayValue()
            barDecoder.close()
        and:
            barDecoder.hasNextArrayValue()
            barDecoder.decodeString() == "zero"
            barDecoder.hasNextArrayValue()
            barDecoder.decodeString() == "one"
            barDecoder.hasNextArrayValue()
            barDecoder.decodeString() == "two"
            !barDecoder.hasNextArrayValue()
            barDecoder.close()
    }

    def 'reuse skip array decoder'() {
        given:
            def outerDecoder = createDecoder("""
{
  "foo": {"bar": ["zero", "one", "two"]}
}
""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
        when: "first read"
            def fooDecoder = nextRoot.decodeObject()
        then:
            fooDecoder.decodeKey() == "bar"
            fooDecoder.skipValue()
            fooDecoder.close()
        and:
            fooDecoder.decodeKey() == "bar"
            def arrayDecoder = fooDecoder.decodeArray()
            arrayDecoder.decodeString() == "zero"
            arrayDecoder.decodeString() == "one"
            arrayDecoder.decodeString() == "two"
    }

    def 'reuse skip object decoder'() {
        given:
            def outerDecoder = createDecoder("""
{
  "foo": [{"bar" : "abc"}, "one", "two"]
}
""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
            nextRoot.skipValue()
            nextRoot.close()
        then:
            nextRoot.decodeKey() == "foo"
            def array = nextRoot.decodeArray()
            array.decodeObject().decodeKey() == "bar"
    }

    def 'reuse decode node object decoder'() {
        given:
            def outerDecoder = createDecoder("""
{
  "foo": [{"bar" : "abc"}, "one", "two"]
}
""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
            nextRoot.decodeNode()
            nextRoot.close()
        then:
            nextRoot.decodeKey() == "foo"
            def array = nextRoot.decodeArray()
            array.decodeObject().decodeKey() == "bar"
    }

    def 'reuse decode buffer object decoder'() {
        given:
            def outerDecoder = createDecoder("""
{
  "foo": [{"bar" : "abc"}, "one", "two"]
}
""")

            def root = BufferedDecoder.of(outerDecoder)

        when:
            def nextRoot = root.decodeObjectNonConsuming()
        then:
            nextRoot.decodeKey() == "foo"
            def arrayDecoder = nextRoot.decodeArray()
            arrayDecoder.skipValue()
            nextRoot.close()
        and:
            nextRoot.decodeKey() == "foo"
        when:
            arrayDecoder = nextRoot.decodeArray()
        then:
            arrayDecoder.decodeObject().decodeKey() == "bar"
    }

    private static void moveToKeyValue(LookaheadDecoder decoder, String match) {
        for (String key = decoder.decodeKey(); key != null; key = decoder.decodeKey()) {
            if (match == key) {
                return
            } else {
                decoder.skipValue()
            }
        }
        throw new IllegalStateException("Not found: " + match)
    }

    private static Decoder createDecoder(@Language("json") String json) {
        return JacksonDecoder.create(new JsonFactoryBuilder().build().createParser(json), LimitingStream.DEFAULT_LIMITS)
    }
}
