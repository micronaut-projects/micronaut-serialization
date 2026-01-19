/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext

/**
 * Tests serialization and deserialization of beans with Optional getters
 * but plain String setters.
 */
abstract class JsonOptionalGetterSpec extends JsonCompileSpec {

    void "test serialize and deserialize MyThing with Optional getter"() {
        given:
        ApplicationContext context = buildContext('example.MyThing', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Optional;

/**
 * Test class for Optional getter wrapping a String property.
 * This tests the scenario where a getter returns Optional&lt;String&gt; but the
 * setter accepts a plain String.
 */
@Serdeable
public class MyThing {

    private String value;

    public Optional<String> getValue() {
        return Optional.ofNullable(value);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
''')
        def myThing = typeUnderTest.type.newInstance()
        myThing.setValue("someValue")

        when:
        String json = jsonMapper.writeValueAsString(myThing)
        def other = jsonMapper.readValue(json, typeUnderTest)

        then:
        other.getValue().orElse(null) == "someValue"

        cleanup:
        context.close()
    }
}
