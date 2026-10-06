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

abstract class AbstractToonSerializationSpec extends AbstractToonCompileSpec {

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
        def result = writeToon(bean)
        def obj = readToon(result.bytes, typeUnderTest)

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
        def result = writeToon(bean)
        def obj = readToon(result.bytes, typeUnderTest)

        then:
        obj.values() == ["A", "B"]
        obj.counts() == [one: 1, two: 2]

        cleanup:
        context.close()
    }

    void "serialization - scalars are emitted without quotes when safe"() {
        given:
        def context = buildContext('test.Test', '''
        package test;
        import io.micronaut.serde.annotation.Serdeable;

        @Serdeable
        record Test(boolean booleanValue, int integerValue, double decimalValue, String textValue) {}
    ''')
        def bean = newInstance(context, 'test.Test', [true, 42, 3.14d, "hello_world"] as Object[])

        when:
        def result = writeToon(bean)
        def obj = readToon(result.bytes, typeUnderTest)

        then:
        result.contains("booleanValue: true")
        result.contains("integerValue: 42")
        result.contains("decimalValue: 3.14")
        result.contains("textValue: hello_world")
        obj.booleanValue()
        obj.integerValue() == 42
        obj.decimalValue() == 3.14d
        obj.textValue() == "hello_world"

        cleanup:
        context.close()
    }
}
