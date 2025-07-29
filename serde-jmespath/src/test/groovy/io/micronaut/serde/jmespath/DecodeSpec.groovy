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

    def flatten() {
        when:
            def decoder = createDecoder("""
{
      "foo": [{
          "bar": [
            {
              "qux": 2,
              "baz": 1
            },
            {
              "qux": 4,
              "baz": 3
            }
          ]
        },
        {
          "bar": [
            {
              "qux": 6,
              "baz": 5
            },
            {
              "qux": 8,
              "baz": 7
            }
          ]
        }
      ]
    }
""")

            def node = SerdeJmesPathDecoder.decode(
                    decoder,
                    "foo[].bar[].[baz, qux][]"
            )
        then:
            toJson(node) == "[1,2,3,4,5,6,7,8]"
    }

    def flattenSimple() {
        when:
            def decoder = createDecoder("""[[1],2]""")

            def node = SerdeJmesPathDecoder.decode(
                    decoder,
                    "[]"
            )
        then:
            toJson(node) == "[1,2]"
    }

    def flatten2() {
        when:
            def decoder = createDecoder("""
{"reservations": [{
        "instances": [
            {"foo": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]},
            {"foo": [{"bar": 5}, {"bar": 6}, {"notbar": [7]}, {"bar": 8}]},
            {"foo": "bar"},
            {"notfoo": [{"bar": 20}, {"bar": 21}, {"notbar": [7]}, {"bar": 22}]},
            {"bar": [{"baz": [1]}, {"baz": [2]}, {"baz": [3]}, {"baz": [4]}]},
            {"baz": [{"baz": [1, 2]}, {"baz": []}, {"baz": []}, {"baz": [3, 4]}]},
            {"qux": [{"baz": []}, {"baz": [1, 2, 3]}, {"baz": [4]}, {"baz": []}]}
        ],
        "otherkey": {"foo": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]}
      }, {
        "instances": [
            {"a": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]},
            {"b": [{"bar": 5}, {"bar": 6}, {"notbar": [7]}, {"bar": 8}]},
            {"c": "bar"},
            {"notfoo": [{"bar": 23}, {"bar": 24}, {"notbar": [7]}, {"bar": 25}]},
            {"qux": [{"baz": []}, {"baz": [1, 2, 3]}, {"baz": [4]}, {"baz": []}]}
        ],
        "otherkey": {"foo": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]}
      }
    ]}
""")

            def node = SerdeJmesPathDecoder.decode(
                    decoder,
                    "reservations[].instances[].notfoo[].bar"
            )
        then:
            toJson(node) == "[20,21,22,23,24,25]"
    }

    def flatten2x() {
        when:
            def decoder = createDecoder("""
{"reservations": [{
        "instances": [
            {"foo": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]},
            {"foo": [{"bar": 5}, {"bar": 6}, {"notbar": [7]}, {"bar": 8}]},
            {"foo": "bar"},
            {"notfoo": [{"bar": 20}, {"bar": 21}, {"notbar": [7]}, {"bar": 22}]},
            {"bar": [{"baz": [1]}, {"baz": [2]}, {"baz": [3]}, {"baz": [4]}]},
            {"baz": [{"baz": [1, 2]}, {"baz": []}, {"baz": []}, {"baz": [3, 4]}]},
            {"qux": [{"baz": []}, {"baz": [1, 2, 3]}, {"baz": [4]}, {"baz": []}]}
        ],
        "otherkey": {"foo": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]}
      }, {
        "instances": [
            {"a": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]},
            {"b": [{"bar": 5}, {"bar": 6}, {"notbar": [7]}, {"bar": 8}]},
            {"c": "bar"},
            {"notfoo": [{"bar": 23}, {"bar": 24}, {"notbar": [7]}, {"bar": 25}]},
            {"qux": [{"baz": []}, {"baz": [1, 2, 3]}, {"baz": [4]}, {"baz": []}]}
        ],
        "otherkey": {"foo": [{"bar": 1}, {"bar": 2}, {"notbar": 3}, {"bar": 4}]}
      }
    ]}
""")

            def node = SerdeJmesPathDecoder.decode(
                    decoder,
                    "reservations[].instances[].foo[].notbar"
            )
        then:
            toJson(node) == "[3,[7]]"
    }

    def flatten3() {
        when:
            def decoder = createDecoder("""
{
        "foo": [
            [["one", "two"], ["three", "four"]],
            [["five", "six"], ["seven", "eight"]],
            [["nine"], ["ten"]]
        ]
     }
""")

            def node = SerdeJmesPathDecoder.decode(
                    decoder,
                    "foo[]"
            )
        then:
            toJson(node) == """[["one","two"],["three","four"],["five","six"],["seven","eight"],["nine"],["ten"]]"""
    }

    def flatten4() {
        when:
            def decoder = createDecoder("""
{
        "string": "string",
        "hash": {"foo": "bar", "bar": "baz"},
        "number": 23,
        "nullvalue": null
     }
""")

            def node = SerdeJmesPathDecoder.decode(
                    decoder,
                    "string[]"
            )
        then:
            toJson(node) == """null"""
    }

}
