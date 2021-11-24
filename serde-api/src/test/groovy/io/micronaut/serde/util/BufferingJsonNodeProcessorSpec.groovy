package io.micronaut.serde.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import io.micronaut.core.annotation.NonNull
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec
import io.micronaut.json.tree.JsonNode
import org.intellij.lang.annotations.Language
import org.reactivestreams.Processor
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.function.Consumer

class BufferingJsonNodeProcessorSpec extends Specification {
    private static final JsonFactory factory = new JsonFactory()

    private static JsonNode node(@Language('json') String json) {
        return JsonNodeTreeCodec.instance.readTree(factory.createParser(json))
    }

    private static boolean isComplete(JsonParser parser) {
        try {
            parser.nextToken()
            parser.skipChildren()
            return parser.nextToken() == null
        } catch (JsonParseException ignored) {
            return false
        }
    }

    def 'test walkJson'() {
        given:
        def bytes = json.getBytes(StandardCharsets.UTF_8)

        when:
        long state = 0
        for (int i = 0; i < bytes.length; i++) {
            state = BufferingJsonNodeProcessor.walkJson(state, bytes[i])
            assert isComplete(factory.createParser(bytes, 0, i + 1)) == (state == 0)
        }

        then:
        state == 0


        where:
        json << [
                '[   \n  ]',
                '[ "a","abc"] ',
                // tests from JSONTestSuite
                '[[]   ]',
                '[""]',
                '[]',
                '["a"]',
                '[false]',
                '[null, 1, "1", {}]',
                '[1\n]',
                ' [1]',
                '[1,null,null,null,2]',
                '[2] ',
                '[0e+1]',
                '[0e1]',
                '[ 4]',
                '[-0.000000000000000000000000000000000000000000000000000000000000000000000000000001]\n',
                '[20e1]',
                '[-0]',
                '[-123]',
                '[-1]',
                '[1E-2]',
                '[1E+2]',
                '[1E22]',
                '[123e45]',
                '[123.456e78]',
                '[1e-2]',
                '[1e+2]',
                '[123]',
                '[123.456789]',
                '[123e65]',
                '{"asd":"sdf"}',
                '{"a":"b","a":"b"}',
                '{"a":"b","a":"c"}',
                '{"":0}',
                '{}',
                '{"foo\\u0000bar": 42}',
                '{ "min": -1.0e+28, "max": 1.0e+28 }',
                '{"x":[{"id": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}], "id": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}',
                '{"a":[]}',
                '{"title":"\\u041f\\u043e\\u043b\\u0442\\u043e\\u0440\\u0430 \\u0417\\u0435\\u043c\\u043b\\u0435\\u043a\\u043e\\u043f\\u0430" }',
                '{\n' +
                        '"a": "b"\n' +
                        '}',
                '{"asd":"sdf", "dfg":"fgh"}',
                '["\\u0060\\u012a\\u12AB"]',
                '["\\uD801\\udc37"]',
                '["\\ud83d\\ude39\\ud83d\\udc8d"]',
                '["\\"\\\\\\/\\b\\f\\n\\r\\t"]',
                '["\\\\u0000"]',
                '["\\\\u0000"]',
                '["\\""]',
                '["a/*b*/c/*d//e"]',
                '["\\\\a"]',
                '["\\\\n"]',
                '["\\u0012"]',
                '["\\uFFFF"]',
                '[ "asd"]',
                '["asd"]',
                '["\\uDBFF\\uDFFF"]',
                '["new\\u00A0line"]',
                '["􏿿"]',
                '["￿"]',
                '["\\u0000"]',
                '["\\u002c"]',
                '["π"]',
                '["𛿿"]',
                '["asd "]',
                '" "',
                '["\\uD834\\uDd1e"]',
                '["\\u0821"]',
                '["\\u0123"]',
                //'["\n"]',
                '[" "]',
                '["\\u0061\\u30af\\u30EA\\u30b9"]',
                '["new\\u000Aline"]',
                '["\u007F"]',
                '["⍂㈴⍂"]',
                '["\\u0022"]',
                '["\\uD83F\\uDFFE"]',
                '["\\uDBFF\\uDFFE"]',
                '["\\u200B"]',
                '["\\u2064"]',
                '["\\uFDD0"]',
                '["\\uFFFE"]',
                '["\\uA66D"]',
                '["\\u005C"]',
                '["€𝄞"]',
                '["\\u005C"]',
                '["€𝄞"]',
                '["a\u007Fa"]',
        ]
    }

    def 'nodes are forwarded asap'() {
        given:
        def processor = new BufferingJsonNodeProcessorImpl(false)
        def seen = new ArrayDeque<JsonNode>()
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {
            }

            @Override
            void cancel() {
            }
        })
        processor.subscribe(new Subscriber<JsonNode>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(JsonNode node) {
                seen.add(node)
            }

            @Override
            void onError(Throwable t) {
                t.printStackTrace()
            }

            @Override
            void onComplete() {
            }
        })

        when:
        processor.onNext('\"foo\"\n'.getBytes(StandardCharsets.UTF_8))
        then:
        seen.remove() == node('\"foo\"')

        when:
        processor.onNext('42\n'.getBytes(StandardCharsets.UTF_8))
        then:
        seen.remove() == node('42')

        when:
        processor.onNext('{"abc":"def"}\n'.getBytes(StandardCharsets.UTF_8))
        then:
        seen.remove() == node('{"abc":"def"}')

        when:
        processor.onNext('[42]\n'.getBytes(StandardCharsets.UTF_8))
        then:
        seen.remove() == node('[42]')
    }

    def 'chunked json'() {
        given:
        List<JsonNode> expectedNodes = new ArrayList<>()
        String joinedString = data.join("")
        JsonParser parser = factory.createParser(joinedString)
        while (parser.nextToken() != null) {
            expectedNodes.add(JsonNodeTreeCodec.instance.readTree(parser))
        }
        def actualNodes = new ArrayList<JsonNode>()
        def processor = new BufferingJsonNodeProcessorImpl(false)
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {
            }

            @Override
            void cancel() {
            }
        })
        processor.subscribe(new Subscriber<JsonNode>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(JsonNode node) {
                actualNodes.add(node)
            }

            @Override
            void onError(Throwable t) {
                t.printStackTrace()
            }

            @Override
            void onComplete() {
            }
        })

        when:
        for (String chunk : data) {
            processor.onNext(chunk.getBytes(StandardCharsets.UTF_8))
        }
        processor.onComplete()
        then:
        actualNodes == expectedNodes

        where:
        data << [
                ['{"abc', '":"def"', '}', '\n'],
                ['{"abc', '":"def"', '}'],
                ['{"abc":"def"}{"abc":"def"}'],
                ['"abc', '" "def"', '\n'],
                ['4', '2 55 56', '7\n'],
        ]
    }

    def 'array streaming'() {
        given:
        List<JsonNode> expectedNodes = new ArrayList<>()
        String joinedString = data.join("")
        JsonParser parser = factory.createParser(joinedString)
        while (parser.nextToken() != null) {
            expectedNodes.add(JsonNodeTreeCodec.instance.readTree(parser))
        }
        // unpack array
        if (expectedNodes.get(0).isArray()) {
            expectedNodes.addAll(expectedNodes.get(0).values())
            expectedNodes.remove(0)
        }
        def actualNodes = new ArrayList<JsonNode>()
        def processor = new BufferingJsonNodeProcessorImpl(true)
        processor.onSubscribe(new Subscription() {
            @Override
            void request(long n) {
            }

            @Override
            void cancel() {
            }
        })
        processor.subscribe(new Subscriber<JsonNode>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(JsonNode node) {
                actualNodes.add(node)
            }

            @Override
            void onError(Throwable t) {
                t.printStackTrace()
            }

            @Override
            void onComplete() {
            }
        })

        when:
        for (String chunk : data) {
            processor.onNext(chunk.getBytes(StandardCharsets.UTF_8))
        }
        processor.onComplete()
        then:
        actualNodes == expectedNodes

        where:
        data << [
                ['{"abc', '":"def"', '}', '\n'],
                ['[{"abc', '":"def"', '}]'],
                ['["abc', '", "def"]', '\n'],
                ['[4', '2, 55, 56', '7]\n'],
        ]
    }

    static class BufferingJsonNodeProcessorImpl extends BufferingJsonNodeProcessor {

        BufferingJsonNodeProcessorImpl(boolean streamArray) {
            this({}, streamArray)
        }

        BufferingJsonNodeProcessorImpl(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
            super(onSubscribe, streamArray)
        }

        @NonNull
        @Override
        protected JsonNode parseOne(@NonNull InputStream is) throws IOException {
            return JsonNodeTreeCodec.instance.readTree(factory.createParser(is))
        }
    }
}
