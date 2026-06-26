package io.micronaut.serde.yaml;

import io.micronaut.core.type.Argument;

class YamlDeserializationSpec extends YamlCompileSpec {

    void "deserialization - root mapping with record name wrapper"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        @Serdeable
        record Test(String value1, String value2, String value3) {}
    ''')

        expect:
        def obj = yamlMapper.readValue('Test:\n' +
                '  value1: A\n' +
                '  value2: B\n' +
                '  value3: C\n', Argument.of(typeUnderTest.type))
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
        def obj = yamlMapper.readValue(
                'value1: A\nvalue2: B\nvalue3: C\n',
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                'value1: A\nvalue3: C\n',
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                'value1: A\nvalue2: B\nvalue3: C\nextra: Z\n',
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "value1: ''\nvalue2: ~\nvalue3: null\n",
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "value1: 'true'\nvalue2: \"123\"\nvalue3: '001'\n",
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "value1: |\n  line1\n  line2\n",
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "name: X\ninner:\n  a: A\n  b: B\n",
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "values:\n  - A\n  - B\n  - C\n",
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "values:\n  k1: v1\n  k2: v2\n",
                Argument.of(typeUnderTest.type)
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
        def obj = yamlMapper.readValue(
                "i: 1\nl: 9223372036854775807\nb: true\nd: 1.25\n",
                Argument.of(typeUnderTest.type)
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
        yamlMapper.readValue("i: notANumber\n", Argument.of(typeUnderTest.type))

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
        def obj = yamlMapper.readValue("color: RED\n", Argument.of(typeUnderTest.type))
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
        yamlMapper.readValue("", Argument.of(typeUnderTest.type))

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
        yamlMapper.readValue("---\nvalue1: A\n---\nvalue1: B\n", Argument.of(typeUnderTest.type))

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
        yamlMapper.readValue("value1:\n   - A\n  - B\n", Argument.of(typeUnderTest.type))

        then:
        thrown(Exception)

        cleanup:
        context.close()
    }

    void "deserialization - scalar alias anchor usage"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value1, String value2) {}
    ''')

        expect:
        def obj = yamlMapper.readValue("value1: &x A\nvalue2: *x\n", Argument.of(typeUnderTest.type))
        obj.value1() == "A"
        obj.value2() == "A"

        cleanup:
        context.close()
    }

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
        def obj = yamlMapper.readValue(
                "---\n" +
                "hr:\n" +
                "  - Mark McGwire\n" +
                "  # Following node labeled SS\n" +
                "  - &SS Sammy Sosa\n" +
                "rbi:\n" +
                "  - *SS # Subsequent occurrence\n" +
                "  - Ken Griffey\n",
                Argument.of(typeUnderTest.type)
        )
        obj.hr() == ["Mark McGwire", "Sammy Sosa"]
        obj.rbi() == ["Sammy Sosa", "Ken Griffey"]

        cleanup:
        context.close()
    }
}
