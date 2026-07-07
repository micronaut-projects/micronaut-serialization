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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.config.annotation.SerdeConfig
import spock.lang.Ignore

abstract class AbstractYamlSerializationSpec extends AbstractYamlDeserializationSpec {

    void "serialization - record object"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value1, String value2, int count) {}
    ''')
        def bean = newInstance(context, 'test.Test', ["A", "B", 10] as Object[])

        when:
        def result = writeYaml(bean)
        def obj = readYaml(result.bytes, typeUnderTest)

        then:
        obj.value1() == "A"
        obj.value2() == "B"
        obj.count() == 10

        cleanup:
        context.close()
    }

    void "serialization - nested collections"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.List;
        import java.util.Map;

        @Serdeable
        record Test(List<String> values, Map<String, Integer> counts) {}
    ''')
        def bean = newInstance(context, 'test.Test', [["A", "B"], [one: 1, two: 2]] as Object[])

        when:
        def result = writeYaml(bean)
        def obj = readYaml(result.bytes, typeUnderTest)

        then:
        obj.values() == ["A", "B"]
        obj.counts() == [one: 1, two: 2]

        cleanup:
        context.close()
    }

    void "serialization - yaml scalar values are emitted without quotes"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.time.LocalDate;

        @Serdeable
        record Test(boolean booleanValue, int integerValue, double decimalValue, Object nullValue, LocalDate dateValue) {}
    ''', [:], ['micronaut.serde.serialization.inclusion': SerdeConfig.SerInclude.ALWAYS])
        def bean = newInstance(context, 'test.Test', [true, 1, 0.288d, null, java.time.LocalDate.parse("2024-01-01")] as Object[])

        when:
        def result = writeYaml(bean)
        def obj = readYaml(result.bytes, typeUnderTest)

        then:
        result == "booleanValue: true\n" +
                "integerValue: 1\n" +
                "decimalValue: 0.288\n" +
                "nullValue: null\n" +
                "dateValue: 2024-01-01\n"
        obj.booleanValue()
        obj.integerValue() == 1
        obj.decimalValue() == 0.288d
        obj.nullValue() == null
        obj.dateValue() == java.time.LocalDate.parse("2024-01-01")

        cleanup:
        context.close()
    }

    void "serialization - quote reserved YAML string values"() {
        given:
        def context = ApplicationContext.run(getContextProperties())
        initializeMapper(context)

        when:
        def yaml = writeYaml([key: value])
        def roundTrip = readYaml(yaml, Argument.mapOf(String, Object))

        then:
        yaml == 'key: "' + value + '"\n'
        roundTrip.key == value

        cleanup:
        context.close()

        where:
        value << [
                "null", "Null", "NULL",
                "true", "True", "TRUE",
                "false", "False", "FALSE",
                "yes", "Yes", "YES",
                "no", "No", "NO",
                "y", "Y", "n", "N",
                "on", "On", "ON",
                "off", "Off", "OFF"
        ]
    }

    // NOTE: Jackson 3.0 behavior differs from 2.x due to changes in the
    // underlying "snakeyaml-engine" behavior
    void "deserialization - accepts reserved YAML values without quotes"() {
        given:
        def context = ApplicationContext.run(getContextProperties())
        initializeMapper(context)
        // serde-default : ['micronaut.serde.format.yaml.read-features.boolean-as-strings': true]

        expect:
        readYaml('key: ' + value + '\n', Argument.mapOf(String, Object)).key == expected

        cleanup:
        context.close()

        where:
        value   || expected
        "null"  || null
        "Null"  || null
        "NULL"  || null
        "true"  || true
        "True"  || true
        "TRUE"  || true
        "false" || false
        "False" || false
        "FALSE" || false
        "yes"   || "yes"
        "Yes"   || "Yes"
        "YES"   || "YES"
        "no"    || "no"
        "No"    || "No"
        "NO"    || "NO"
        "y"     || "y"
        "Y"     || "Y"
        "n"     || "n"
        "N"     || "N"
        "on"    || "on"
        "On"    || "On"
        "ON"    || "ON"
        "off"   || "off"
        "Off"   || "Off"
        "OFF"   || "OFF"
    }

    protected Map<String, Object> minimizeQuotesConfiguration(boolean enabled) {
        ['micronaut.serde.format.yaml.write-features.minimize-quotes': enabled]
    }

    void "serialization - quoting special yaml characters under minimizing quote configuration"() {
        given:
        def context = buildContext('''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test() {}
        ''', minimizeQuotesConfiguration(true))

        expect:
        writeYaml([key: value]) in expected

        cleanup:
        context.close()

        where:
        value || expected
        // these are safe plain scalars. they submit to configuration
        // will be quoted if —> minimizeQuotesConfiguration(false)
        'a:b' || ['key: a:b\n']      // : followed by b not blank
        'f:off' || ['key: f:off\n']
        'a#b' || ['key: a#b\n']     //  # — Have to be first to be a comment
        'a# b' || ['key: a# b\n']

         132 || ['key: 132\n']
        '"132"' || ['key: \'"132"\'\n']

        // these don't submit to configuration
        'a: b' || ['key: "a: b"\n']
        '::' || ['key: "::"\n']
        '#' || ['key: "#"\n']
        '#a' || ['key: "#a"\n']
        'a[b' || ['key: "a[b"\n']
        'a]b' || ['key: "a]b"\n']
        'a{b' || ['key: "a{b"\n']
        'a}b' || ['key: "a}b"\n']
        'a,b' || ['key: "a,b"\n']
    }

    void "serialization - minimize quotes preserves boolean-like strings"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String trueString, String falseString, String text, boolean booleanValue) {}
    ''', [:],
                minimizeQuotesConfiguration(true))

        def bean = newInstance(context, 'test.Test', ["true", "false", "something else", true] as Object[])

        when:
        def result = writeYaml(bean)
        def obj = readYaml(result.bytes, typeUnderTest)

        then:
        result == '''trueString: "true"
falseString: "false"
text: something else
booleanValue: true
'''
        obj.trueString() == "true"
        obj.falseString() == "false"
        obj.text() == "something else"
        obj.booleanValue()

        cleanup:
        context.close()
    }

    void "serialization - configured minimize quotes"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value, boolean booleanValue) {}
    ''', [:],
                minimizeQuotesConfiguration(false))

        def bean = newInstance(context, 'test.Test', ["something else", true] as Object[])

        expect:
        writeYaml(bean) == '''value: "something else"
booleanValue: true
'''

        cleanup:
        context.close()
    }

    @Ignore("The Jackson Yaml Databind fail as well, sice this feature was merged into 2.19, not the released Jackson 3 we use")
    void "serialization - configured literal block style multiline with trailing space"() {
        given:
        def context = ApplicationContext.run(getContextProperties() + [
                'micronaut.serde.format.yaml.write-features.literal-block-style': true
        ])
        initializeMapper(context)

        when:
        def yaml = writeYaml([text: "Hello\nWorld "]).trim()

        then:
        yaml == "text: |-\n  Hello\n  World "

        cleanup:
        context.close()
    }

    void "serialization - configured split lines"() {
        given:
        def context = ApplicationContext.run(getContextProperties() + [
                'micronaut.serde.format.yaml.write-features.minimize-quotes': false,
                'micronaut.serde.format.yaml.write-features.split-lines'    : splitLines
        ])
        initializeMapper(context)

        when:
        def yaml = writeYaml([
                "1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890"
        ]).trim()

        then:
        yaml == expected

        cleanup:
        context.close()

        where:
        splitLines || expected
        true       || "- \"1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890\\\n  \\ 1234567890\""
        false      || "- \"1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890\""
    }

    void "serialization - configured write style"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.List;
        import java.util.Map;

        @Serdeable
        record Test(List<String> values, Map<String, Integer> counts) {}
    ''', [:], ['micronaut.serde.format.yaml.write-features.write-style': writeStyle])

        def bean = newInstance(context, 'test.Test', [["A", "B"], [one: 1, two: 2]] as Object[])

        expect:
        writeYaml(bean) == expected

        cleanup:
        context.close()

        where:
        writeStyle || expected
        'BLOCK'    || "values:\n- A\n- B\ncounts:\n  one: 1\n  two: 2\n"
        'FLOW'     || "{values: [A, B], counts: {one: 1, two: 2}}\n"
    }

    void "serialization - configured explicit document markers"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(String value) {}
    ''', [:], [
                'micronaut.serde.format.yaml.write-features.explicit-start': explicitStart,
                'micronaut.serde.format.yaml.write-features.explicit-end'  : explicitEnd
        ])
        def bean = newInstance(context, 'test.Test', ["A"] as Object[])

        expect:
        writeYaml(bean) == expected

        cleanup:
        context.close()

        where:
        explicitStart | explicitEnd || expected
        true          | true        || "---\nvalue: A\n...\n"
        true          | false       || "---\nvalue: A\n"
        false         | true        || "value: A\n...\n"
    }

    void "serialization - configured indent"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;
        import java.util.Map;

        @Serdeable
        record Test(Map<String, Integer> counts) {}
    ''', [:], ['micronaut.serde.format.yaml.write-features.indent': 4])
        def bean = newInstance(context, 'test.Test', [[one: 1, two: 2]] as Object[])

        expect:
        writeYaml(bean) == "counts:\n    one: 1\n    two: 2\n"

        cleanup:
        context.close()
    }
}
