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
package io.micronaut.serde.toon.tck

import io.micronaut.core.type.Argument

abstract class AbstractToonDeserializationSpec extends AbstractToonCompileSpec {

    void "deserialization - record direct fields"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = readToon('value1: A\nvalue2: B\nvalue3: C\n', typeUnderTest)
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
        def obj = readToon('value1: A\nvalue3: C\n', typeUnderTest)
        obj.value1() == "A"
        obj.value2() == null
        obj.value3() == "C"

        cleanup:
        context.close()
    }

    void "deserialization - extra unknown field is ignored"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = readToon('value1: A\nvalue2: B\nvalue3: C\nextra: IgnoredValue\n', typeUnderTest)
        obj.value1() == "A"
        obj.value2() == "B"
        obj.value3() == "C"

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
        def obj = readToon("name: X\ninner:\n  a: A\n  b: B\n", typeUnderTest)
        obj.name() == "X"
        obj.inner().a() == "A"
        obj.inner().b() == "B"

        cleanup:
        context.close()
    }

    void "deserialization - list field"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.List;

        @Serdeable
        record Test(List<String> values) {}
    ''')

        expect:
        def obj = readToon("values[3]:\n  - A\n  - B\n  - C\n", typeUnderTest)
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
        def obj = readToon("values:\n  k1: v1\n  k2: v2\n", typeUnderTest)
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
        def obj = readToon("i: 1\nl: 9223372036854775807\nb: true\nd: 1.25\n", typeUnderTest)
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
        readToon("i: notANumber\n", typeUnderTest)

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
        def obj = readToon("color: RED\n", typeUnderTest)
        obj.color().name() == "RED"

        cleanup:
        context.close()
    }

    void "deserialization - an empty document decodes to defaults, not an error"() {
        given:
        // TOON's grammar defines an empty document as an empty object,
        // unlike YAML (where an empty input produces no mapping node at
        // all, so binding never even starts). Once there is an object to
        // bind, a missing non-@Nullable field simply stays null rather
        // than failing - that part is generic DeserBean behavior shared
        // by every format, not specific to TOON.
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1) {}
    ''')

        expect:
        def obj = readToon("", typeUnderTest)
        obj.value1() == null

        cleanup:
        context.close()
    }

    void "deserialization - trailing content after the root value is rejected"() {
        when:
        readToon('[2]: a,b\nextra: value\n', Argument.mapOf(String, Object))

        then:
        thrown(Exception)
    }

    void "deserialization - inconsistent sibling indentation should fail"() {
        given:
        def context = buildContext('test.Outer', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Inner(String name, int age) {}

        @Serdeable
        record Outer(Inner address) {}
    ''')

        when:
        // "age" is indented one space deeper than its sibling "name".
        readToon("address:\n  name: Alice\n   age: 30\n", typeUnderTest)

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }
}
