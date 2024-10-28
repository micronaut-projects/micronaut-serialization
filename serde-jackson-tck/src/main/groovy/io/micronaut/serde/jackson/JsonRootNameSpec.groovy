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

abstract class JsonRootNameSpec extends JsonCompileSpec {

    void "test basic JsonRootName"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonRootName(value = "sampleClass")
record SampleClass(String a, String b) {}
""")

        when:
        def instance = newInstance(context, 'test.SampleClass', "xyz", "abc")
        def json = writeJson(jsonMapper, instance)

        then:
        json == """{"sampleClass":{"a":"xyz","b":"abc"}}"""

        when:
        def deser = jsonMapper.readValue(json, argumentOf(context, 'test.SampleClass'))

        then:
        deser.a == "xyz"
        deser.b == "abc"

        cleanup:
        context.close()
    }

     void "test deserialize from root value {}"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonRootName(value = "sampleClass")
record SampleClass(String a, String b) {}
""")

        when:
        def deserNull = jsonMapper.readValue("""{"sampleClass":{}}""", argumentOf(context, 'test.SampleClass'))

        then:
        deserNull.a == null
        deserNull.b == null

        cleanup:
        context.close()
    }

}
