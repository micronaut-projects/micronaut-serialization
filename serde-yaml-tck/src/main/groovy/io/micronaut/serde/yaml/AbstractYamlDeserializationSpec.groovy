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
package io.micronaut.serde.yaml

import io.micronaut.core.type.Argument
import spock.lang.IgnoreIf

abstract class AbstractYamlDeserializationSpec extends AbstractYamlCompileSpec {

    void "deserialization - root mapping with record name wrapper"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = readYamlWithRootWrapper('Test:\n' +
                '  value1: A\n' +
                '  value2: B\n' +
                '  value3: C\n', typeUnderTest)
        obj.value1() == "A"
        obj.value2() == "B"
        obj.value3() == "C"

        cleanup:
        context.close()
    }

    void "deserialization - root mapping without wrapper (direct fields)"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = readYaml(
                'value1: A\nvalue2: B\nvalue3: C\n',
                typeUnderTest
        )
        obj.value1() == "A"
        obj.value2() == "B"
        obj.value3() == "C"

        cleanup:
        context.close()
    }

    void "deserialization - missing optional-like nullable field becomes null"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import io.micronaut.core.annotation.Nullable;
        @Serdeable
        record Test(String value1, @Nullable String value2, String value3) {}
    ''')

        expect:
        def obj = readYaml(
                'value1: A\nvalue3: C\n',
                typeUnderTest
        )
        obj.value1() == "A"
        obj.value2() == null
        obj.value3() == "C"

        cleanup:
        context.close()
    }

    void "deserialization - extra unknown field (decide: ignore or fail) - here expects ignore"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = readYaml(
                'value1: A\nvalue2: B\nvalue3: C\nextra: Z\n',
                typeUnderTest
        )
        obj.value1() == "A"
        obj.value2() == "B"
        obj.value3() == "C"

        cleanup:
        context.close()
    }

    void "deserialization - empty string and explicit null"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import io.micronaut.core.annotation.Nullable;
        @Serdeable
        record Test(String value1, @Nullable String value2, @Nullable String value3) {}
    ''')

        expect:
        def obj = readYaml(
                "value1: ''\nvalue2: ~\nvalue3: null\n",
                typeUnderTest
        )
        obj.value1() == ""
        obj.value3() == null
        obj.value2() == null

        cleanup:
        context.close()
    }

    void "deserialization - quoted scalars that look like booleans/numbers should remain strings"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = readYaml(
                "value1: 'true'\nvalue2: \"123\"\nvalue3: '001'\n",
                typeUnderTest
        )
        obj.value1() == "true"
        obj.value2() == "123"
        obj.value3() == "001"

        cleanup:
        context.close()
    }

    void "deserialization - multiline block scalar"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1) {}
    ''')

        expect:
        def obj = readYaml(
                "value1: |\n  line1\n  line2\n",
                typeUnderTest
        )
        obj.value1() == "line1\nline2\n"

        cleanup:
        context.close()
    }

    void "deserialization - nested record object"() {
        given:
        def context = buildContext('test.Outer', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Inner(String a, String b) {}

        @Serdeable
        record Outer(String name, Inner inner) {}
    ''')

        expect:
        def obj = readYaml(
                "name: X\ninner:\n  a: A\n  b: B\n",
                typeUnderTest
        )
        obj.name() == "X"
        obj.inner().a() == "A"
        obj.inner().b() == "B"

        cleanup:
        context.close()
    }

    void "deserialization - list/sequence field"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.List;

        @Serdeable
        record Test(List<String> values) {}
    ''')

        expect:
        def obj = readYaml(
                "values:\n  - A\n  - B\n  - C\n",
                typeUnderTest
        )
        obj.values() == ["A", "B", "C"]

        cleanup:
        context.close()
    }

    void "deserialization - map field"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.Map;

        @Serdeable
        record Test(Map<String, String> values) {}
    ''')

        expect:
        def obj = readYaml(
                "values:\n  k1: v1\n  k2: v2\n",
                typeUnderTest
        )
        obj.values() == [k1: "v1", k2: "v2"]

        cleanup:
        context.close()
    }

    void "deserialization - numeric and boolean types"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(int i, long l, boolean b, double d) {}
    ''')

        expect:
        def obj = readYaml(
                "i: 1\nl: 9223372036854775807\nb: true\nd: 1.25\n",
                typeUnderTest
        )
        obj.i() == 1
        obj.l() == 9223372036854775807L
        obj.b() == true
        obj.d() == 1.25d

        cleanup:
        context.close()
    }

    void "deserialization - invalid numeric should fail type coercion"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(int i) {}
    ''')

        when:
        readYaml("i: notANumber\n", typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "deserialization - enum field"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        enum Color { RED, GREEN }

        @Serdeable
        record Test(Color color) {}
    ''')

        expect:
        def obj = readYaml("color: RED\n", typeUnderTest)
        obj.color().name() == "RED"

        cleanup:
        context.close()
    }

    void "deserialization - empty document should fail"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value1) {}
    ''')

        when:
        readYaml("", typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "deserialization - multiple YAML documents should fail (single-doc policy)"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value1) {}
    ''')

        when:
        readYaml("---\nvalue1: A\n---\nvalue1: B\n", typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "deserialization - invalid indentation should fail"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value1) {}
    ''')

        when:
        readYaml("value1:\n   - A\n  - B\n", typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    @IgnoreIf(
        reason = 'Jackson YAML relies on the low-level SnakeYAML Engine parser and does not compose alias references',
        value = { instance.ignoreYamlAliases() }
    )
    void "deserialization - scalar alias anchor usage"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value1, String value2) {}
    ''')

        expect:
        def obj = readYaml("value1: &x A\nvalue2: *x\n", typeUnderTest)
        obj.value1() == "A"
        obj.value2() == "A"

        cleanup:
        context.close()
    }

    @IgnoreIf(
        reason = 'Jackson YAML relies on the low-level SnakeYAML Engine parser and does not compose alias references',
        value = { instance.ignoreYamlAliases() }
    )
    void "deserialization - yaml specification example 2.10 repeated scalar node"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.List;

        @Serdeable
        record Test(List<String> hr, List<String> rbi) {}
    ''')

        expect:
        def obj = readYaml(
                "---\n" +
                "hr:\n" +
                "  - Mark McGwire\n" +
                "  # Following node labeled SS\n" +
                "  - &SS Sammy Sosa\n" +
                "rbi:\n" +
                "  - *SS # Subsequent occurrence\n" +
                "  - Ken Griffey\n",
                typeUnderTest
        )
        obj.hr() == ["Mark McGwire", "Sammy Sosa"]
        obj.rbi() == ["Sammy Sosa", "Ken Griffey"]

        cleanup:
        context.close()
    }

    protected boolean ignoreYamlAliases() {
        false
    }

    protected <T> T readYamlWithRootWrapper(String yaml, Argument<T> type) {
        readYaml(yaml, type)
    }

}
