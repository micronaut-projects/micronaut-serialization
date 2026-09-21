/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde

import io.micronaut.core.type.Argument
import io.micronaut.serde.patch.JsonPatch
import io.micronaut.serde.patch.JsonPatchException
import io.micronaut.serde.patch.JsonPatchOptions
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.nio.file.Files

abstract class AbstractJsonPatchSpec extends Specification {
    ObjectMapper mapper = ObjectMapper.getDefault()

    static InputStream input(String json) {
        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
    }

    JsonPatch patch(String json) {
        mapper.readJsonPatch(input(json))
    }

    Object apply(String source, String patchJson, JsonPatchOptions options = JsonPatchOptions.DEFAULT) {
        def output = new ByteArrayOutputStream()
        mapper.writePatchedValue(input(source), patch(patchJson), output, options)
        parseValue(output.toString(StandardCharsets.UTF_8))
    }

    Object parseValue(String json) {
        // JSON-P's ordinary byte-array reader cannot auto-detect a one-byte scalar's encoding.
        mapper.readValue('[' + json + ']', Object).get(0)
    }

    @Unroll
    def 'operation semantics: #source with #operations'() {
        expect:
        apply(source, operations) == parseValue(expected)
        apply(source, operations, JsonPatchOptions.DEFAULT.withValidationBeforeWrite(false)) == parseValue(expected)
        mapper.readPatchedValue(input(source), patch(operations), Argument.OBJECT_ARGUMENT) == parseValue(expected)

        where:
        source | operations | expected
        '{"foo":"bar"}' | '[{"op":"add","path":"/baz","value":"qux"}]' | '{"foo":"bar","baz":"qux"}'
        '{"a":1}' | '[{"op":"add","path":"/a","value":null}]' | '{"a":null}'
        '{"a":1,"b":2}' | '[{"op":"remove","path":"/a"}]' | '{"b":2}'
        '{"a":1}' | '[{"op":"replace","path":"/a","value":{"b":[2,3]}}]' | '{"a":{"b":[2,3]}}'
        '[0,1,2]' | '[{"op":"add","path":"/1","value":9}]' | '[0,9,1,2]'
        '[0,1,2]' | '[{"op":"add","path":"/3","value":9}]' | '[0,1,2,9]'
        '[0]' | '[{"op":"add","path":"/-","value":[1,2]}]' | '[0,[1,2]]'
        '[]' | '[{"op":"add","path":"/0","value":1}]' | '[1]'
        '[0,1,2]' | '[{"op":"remove","path":"/1"},{"op":"replace","path":"/1","value":9}]' | '[0,9]'
        '[0,1,2,3]' | '[{"op":"move","from":"/3","path":"/0"}]' | '[3,0,1,2]'
        '[0,1,2,3]' | '[{"op":"move","from":"/1","path":"/3"}]' | '[0,2,3,1]'
        '[0,1,2]' | '[{"op":"move","from":"/1","path":"/1"}]' | '[0,1,2]'
        '[0,1,2]' | '[{"op":"move","from":"/0","path":"/-"}]' | '[1,2,0]'
        '{"a":{"v":1}}' | '[{"op":"copy","from":"/a","path":"/b"},{"op":"replace","path":"/a/v","value":2}]' | '{"a":{"v":2},"b":{"v":1}}'
        '{"a":{"b":1}}' | '[{"op":"copy","from":"/a","path":"/a/c"}]' | '{"a":{"b":1,"c":{"b":1}}}'
        '{"a":1}' | '[{"op":"copy","from":"","path":"/snapshot"}]' | '{"a":1,"snapshot":{"a":1}}'
        '{"a":{"b":[1]}}' | '[{"op":"move","from":"/a/b","path":"/a"}]' | '{"a":[1]}'
        '{"a":1}' | '[{"op":"move","from":"/a","path":""}]' | '1'
        '{"a":1}' | '[{"op":"move","from":"","path":""}]' | '{"a":1}'
        '{"a":1}' | '[{"op":"remove","path":""},{"op":"add","path":"","value":[2]}]' | '[2]'
        'null' | '[{"op":"replace","path":"","value":42}]' | '42'
        '1' | '[{"op":"add","path":"","value":null}]' | 'null'
        '{"a/b":{"m~n":1},"":2,"~1":3}' | '[{"op":"replace","path":"/a~1b/m~0n","value":4},{"op":"remove","path":"/"},{"op":"test","path":"/~01","value":3}]' | '{"a/b":{"m~n":4},"~1":3}'
        '{"01":1,"-":2}' | '[{"op":"replace","path":"/01","value":3},{"op":"remove","path":"/-"}]' | '{"01":3}'
        '{"a":1,"b":[true,null]}' | '[{"op":"test","path":"","value":{"b":[true,null],"a":1.00}}]' | '{"a":1,"b":[true,null]}'
        '9007199254740993' | '[{"op":"test","path":"","value":9007199254740993.0}]' | '9007199254740993'
        '0.123456789012345678901' | '[{"op":"test","path":"","value":0.123456789012345678901}]' | '0.123456789012345678901'
        '{"a":1}' | '[{"path":"/a","extra":{"anything":[1]},"value":2,"from":false,"op":"replace"}]' | '{"a":2}'
        '{"a":1}' | '[{"op":"remove","path":"/a","value":{"x":1,"x":2}}]' | '{}'
        '{"a":1,"ab":{}}' | '[{"op":"move","from":"/a","path":"/ab/x"}]' | '{"ab":{"x":1}}'
        'false' | '[]' | 'false'
    }

    @Unroll
    def 'invalid target or failed test: #source with #operations'() {
        given:
        def output = new ByteArrayOutputStream()

        when:
        mapper.writePatchedValue(input(source), patch(operations), output)

        then:
        thrown(IOException)
        output.size() == 0

        where:
        source | operations
        '{}' | '[{"op":"add","path":"/missing/child","value":1}]'
        '{}' | '[{"op":"remove","path":"/missing"}]'
        '{}' | '[{"op":"replace","path":"/missing","value":1}]'
        '[1]' | '[{"op":"add","path":"/2","value":1}]'
        '[1]' | '[{"op":"remove","path":"/-"}]'
        '[1]' | '[{"op":"replace","path":"/01","value":1}]'
        '[1]' | '[{"op":"add","path":"/+1","value":1}]'
        '[1]' | '[{"op":"add","path":"/99999999999999999999999","value":1}]'
        '{"a":1,"a":2}' | '[{"op":"replace","path":"/a","value":3}]'
        '{"a":1,"a":2}' | '[{"op":"copy","from":"/a","path":"/b"}]'
        '{}' | '[{"op":"copy","from":"/a","path":"/b"}]'
        '{"a":1}' | '[{"op":"move","from":"/missing","path":"/missing"}]'
        '{"a":1}' | '[{"op":"test","path":"/a","value":"1"}]'
        '[1,2]' | '[{"op":"test","path":"","value":[2,1]}]'
        'true' | '[{"op":"test","path":"","value":1}]'
        '9007199254740993' | '[{"op":"test","path":"","value":9007199254740992}]'
        'null' | '[{"op":"remove","path":""}]'
        '{"a":1} {}' | '[]'
        '{"a":' | '[]'
        '' | '[]'
    }

    @Unroll
    def 'malformed patch: #json'() {
        when:
        patch(json)

        then:
        thrown(IOException)

        where:
        json << ['{}', '[1]', '[{}]', '[{"op":"foo","path":""}]',
                 '[{"op":"add","path":""}]', '[{"op":"replace","path":""}]',
                 '[{"op":"test","path":""}]', '[{"op":"copy","path":""}]',
                 '[{"op":"remove","path":1}]', '[{"op":1,"path":""}]',
                 '[{"op":"remove","path":"bad"}]', '[{"op":"remove","path":"/~2"}]',
                 '[{"op":"remove","path":"/~"}]', '[{"op":"remove","op":"add","path":"","value":1}]',
                 '[{"op":"remove","path":"","path":"/a"}]',
                 '[{"op":"move","from":"/a","path":"/a/b"}]',
                 '[{"op":"move","from":"","path":"/a"}]', '[] []']
    }

    def 'later operations cannot hide an earlier failure'() {
        when:
        apply('{"a":1}', '[{"op":"remove","path":"/missing"},{"op":"replace","path":"","value":null}]')

        then:
        def error = thrown(JsonPatchException)
        error.operationIndex == 0
        error.operation == 'remove'
        error.pointer == '/missing'
    }

    def 'materializes a fresh immutable record and validates unknown properties'() {
        given:
        def change = patch('[{"op":"replace","path":"/foo","value":"new"},{"op":"test","path":"/unknown","value":1}]')

        expect:
        mapper.readPatchedValue(input('{"foo":"old","bar":"b","unknown":1}'), change, Argument.of(RecordBean)) == new RecordBean('new', 'b')

        when:
        mapper.readPatchedValue(input('{"foo":"old","bar":"b","unknown":2}'), change, Argument.of(RecordBean))

        then:
        thrown(JsonPatchException)
    }

    def 'numeric results retain precision and reject integral overflow'() {
        expect:
        mapper.readPatchedValue(input('0'), patch('[{"op":"replace","path":"","value":0.123456789012345678901}]'), Argument.of(BigDecimal)) == new BigDecimal('0.123456789012345678901')

        when:
        mapper.readPatchedValue(input('2147483648'), patch('[]'), Argument.of(Integer))

        then:
        thrown(IOException)

        when:
        mapper.readPatchedValue(input('9223372036854775808'), patch('[]'), Argument.of(Long))

        then:
        thrown(IOException)
    }

    def 'patches are reusable and support generic result arguments'() {
        given:
        def change = patch('[{"op":"replace","path":"/0/foo","value":"new"}]')

        expect:
        (1..3).every {
            mapper.readPatchedValue(input('[{"foo":"old","bar":"b"}]'), change, Argument.listOf(RecordBean)) == [new RecordBean('new', 'b')]
        }
    }

    def 'direct output needs no replay memory for a single local operation'() {
        given:
        def options = new JsonPatchOptions(0, 10_000_000, 10, 1000, null, false)
        String source = '{"values":[' + (['1'] * 100_000).join(',') + '],"change":0}'
        def output = OutputStream.nullOutputStream()

        when:
        mapper.writePatchedValue(input(source), patch('[{"op":"replace","path":"/change","value":1}]'), output, options)

        then:
        noExceptionThrown()
    }

    def 'independent top-level edits share one streaming pass without replay memory'() {
        given:
        def options = new JsonPatchOptions(0, 10_000_000, 10, 1000, null, false)
        def operations = '[{"op":"replace","path":"/last","value":9},{"op":"remove","path":"/first"},{"op":"add","path":"/new","value":{"x":1}}]'

        expect:
        apply('{"first":0,"other":[1,2,3],"last":4}', operations, options) == [other:[1,2,3], last:9, new:[x:1]]
        apply('{"last":4,"first":0,"new":0}', operations, options) == [last:9, new:[x:1]]
    }

    def 'fused edits report the earliest operation failure regardless of input order'() {
        given:
        def options = JsonPatchOptions.DEFAULT.withValidationBeforeWrite(false)

        when:
        apply('{"a":1,"a":2}', '[{"op":"remove","path":"/missing"},{"op":"replace","path":"/a","value":9}]', options)

        then:
        def first = thrown(JsonPatchException)
        first.operationIndex == 0
        first.pointer == '/missing'

        when:
        apply('{"a":1,"a":2}', '[{"op":"replace","path":"/a","value":9},{"op":"remove","path":"/missing"}]', options)

        then:
        def second = thrown(JsonPatchException)
        second.operationIndex == 0
        second.pointer == '/a'
    }

    def 'fused edits agree with sequential application over randomized member order'() {
        given:
        def random = new Random(32)
        def operations = '[{"op":"replace","path":"/a","value":[1]},{"op":"remove","path":"/b"},{"op":"add","path":"/d","value":null}]'
        def parsed = patch(operations)

        expect:
        (1..30).every {
            def keys = ['a','b','c']
            Collections.shuffle(keys, random)
            Map document = keys.collectEntries { [(it): random.nextInt(100)] }
            String source = mapper.writeValueAsString(document)
            def sequential = source
            parsed.operations().each { operation ->
                def output = new ByteArrayOutputStream()
                mapper.writePatchedValue(input(sequential), new JsonPatch([operation]), output)
                sequential = output.toString(StandardCharsets.UTF_8)
            }
            apply(source, operations) == parseValue(sequential)
        }
    }

    def 'spill files are cleaned after success and late failure'() {
        given:
        def directory = Files.createTempDirectory('json-patch-test-')
        def options = new JsonPatchOptions(0, 10_000_000, 100, 10000, directory, true)
        String source = '[0,1,2,3]'

        expect:
        apply(source, '[{"op":"move","from":"/3","path":"/0"}]', options) == [3,0,1,2]
        empty(directory)

        when:
        apply(source, '[{"op":"copy","from":"/0","path":"/-"},{"op":"test","path":"/1","value":7}]', options)

        then:
        thrown(JsonPatchException)
        empty(directory)

        cleanup:
        Files.delete(directory)
    }

    def 'memory and disk quotas fail without leaving files or publishing output'() {
        given:
        def directory = Files.createTempDirectory('json-patch-limit-')
        def output = new ByteArrayOutputStream()

        when:
        mapper.writePatchedValue(input('[1,2,3]'), patch('[]'), output, new JsonPatchOptions(0, 10000, 10, 1000, null, true))

        then:
        thrown(IOException)
        output.size() == 0

        when:
        mapper.writePatchedValue(input('[1,2,3]'), patch('[]'), output, new JsonPatchOptions(0, 5, 10, 1000, directory, true))

        then:
        thrown(IOException)
        output.size() == 0
        empty(directory)

        cleanup:
        Files.delete(directory)
    }

    def 'caller owns input and output streams'() {
        given:
        def source = new TrackedInput('{"a":1}')
        def patchInput = new TrackedInput('[{"op":"replace","path":"/a","value":2}]')
        def output = new TrackedOutput()

        when:
        mapper.writePatchedValue(source, mapper.readJsonPatch(patchInput), output)

        then:
        !source.closed
        !patchInput.closed
        !output.closed
    }

    def 'random dependent array operations agree with an independent list model'() {
        given:
        def random = new Random(6902)

        expect:
        (1..40).every {
            List<Integer> expected = [0,1,2,3,4]
            List<Map> operations = []
            20.times {
                int kind = random.nextInt(5)
                if (expected.empty || kind == 0) {
                    int index = random.nextInt(expected.size() + 1)
                    int value = random.nextInt(100)
                    operations.add([op:'add', path:"/$index".toString(), value:value])
                    expected.add(index, value)
                } else {
                    int from = random.nextInt(expected.size())
                    if (kind == 1) {
                        operations.add([op:'remove', path:"/$from".toString()])
                        expected.remove(from)
                    } else if (kind == 2) {
                        int value = random.nextInt(100)
                        operations.add([op:'replace', path:"/$from".toString(), value:value])
                        expected.set(from, value)
                    } else {
                        int value = expected.get(from)
                        if (kind == 3) expected.remove(from)
                        int to = random.nextInt(expected.size() + 1)
                        operations.add([op:kind == 3 ? 'move' : 'copy', from:"/$from".toString(), path:"/$to".toString()])
                        expected.add(to, value)
                    }
                }
            }
            apply('[0,1,2,3,4]', mapper.writeValueAsString(operations)) == expected
        }
    }

    def 'generated and runtime deserializers preserve mapper coercion policy'() {
        given:
        def change = patch('[{"op":"replace","path":"/foo","value":42}]')

        expect:
        [false, true].every { runtime ->
            try (def configured = ObjectMapper.create(['micronaut.serde.deserialization.disable-generated-deserializer': runtime])) {
                assert configured.readPatchedValue(input('{"foo":"old","bar":"b"}'), change, Argument.of(RecordBean)).foo() == '42'
                assert configured.readJsonPatch(input('[]')).operations().empty
            }
            true
        }

        when:
        try (def strict = ObjectMapper.create(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])) {
            strict.readPatchedValue(input('{"foo":"old","bar":"b"}'), change, Argument.of(RecordBean))
        }

        then:
        thrown(IOException)
    }

    def 'depth limits cover skipped input and copied output'() {
        given:
        def configured = ObjectMapper.create(['micronaut.serde.maximum-nesting-depth': 4])
        def change = patch('[{"op":"replace","path":"","value":0}]')

        when:
        configured.writePatchedValue(input('[[[[[1]]]]]'), change, new ByteArrayOutputStream())

        then:
        thrown(IOException)

        when:
        configured.writePatchedValue(input('{"a":{"b":{}}}'), patch('[{"op":"copy","from":"","path":"/a/b/deep"}]'), new ByteArrayOutputStream())

        then:
        thrown(IOException)

        cleanup:
        configured.close()
    }

    def 'operation and patch size limits are enforced when parsing and applying'() {
        given:
        def limited = new JsonPatchOptions(8192, 100000, 1, 100, null, true)
        def two = '[{"op":"add","path":"/a","value":1},{"op":"add","path":"/b","value":2}]'

        when:
        mapper.readJsonPatch(input(two), limited)

        then:
        thrown(IOException)

        when:
        mapper.writePatchedValue(input('{}'), patch(two), new ByteArrayOutputStream(), limited)

        then:
        thrown(IOException)

        when:
        mapper.readJsonPatch(input('[{"op":"test","path":"","value":"' + ('x' * 101) + '"}]'), limited)

        then:
        thrown(IOException)
    }

    def 'output failures also release all spill files'() {
        given:
        def directory = Files.createTempDirectory('json-patch-output-')
        def options = JsonPatchOptions.DEFAULT.withSpillDirectory(directory)
        options = new JsonPatchOptions(0, options.storageLimit(), 10, 1000, directory, true)
        def broken = new OutputStream() {
            @Override
            void write(int value) throws IOException { throw new IOException('destination failed') }
        }

        when:
        mapper.writePatchedValue(input('[1,2,3]'), patch('[]'), broken, options)

        then:
        thrown(Exception)
        empty(directory)

        cleanup:
        Files.delete(directory)
    }

    private static boolean empty(java.nio.file.Path directory) {
        try (def files = Files.list(directory)) {
            return files.count() == 0
        }
    }

    private static class TrackedInput extends ByteArrayInputStream {
        boolean closed
        TrackedInput(String json) {
            super(json.getBytes(StandardCharsets.UTF_8))
        }
        @Override
        void close() { closed = true }
    }

    private static class TrackedOutput extends ByteArrayOutputStream {
        boolean closed
        @Override
        void close() { closed = true }
    }
}
