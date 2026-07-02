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

import io.micronaut.serde.config.annotation.SerdeConfig

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
