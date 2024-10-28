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

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import spock.lang.Unroll

abstract class JsonNamingSpec extends JsonCompileSpec {

    @Unroll
    void "test json naming strategy jackson databind: #strategy"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(${strategy.class.canonicalName}.class)
class Test {
    private String fooBar;
    public void setFooBar(String fooBar) {
        this.fooBar = fooBar;
    }
    public String getFooBar() {
         return fooBar;
    }
}
""", [fooBar: 'test'])
        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == "{\"$name\":\"test\"}"

        when:
        beanUnderTest = jsonMapper.readValue(result, typeUnderTest)

        then:
        beanUnderTest.fooBar == "test"

        cleanup:
        context.close()

        where:
        strategy                                  | name
        PropertyNamingStrategies.LOWER_CAMEL_CASE | "fooBar"
        PropertyNamingStrategies.KEBAB_CASE       | "foo-bar"
        PropertyNamingStrategies.SNAKE_CASE       | "foo_bar"
        PropertyNamingStrategies.UPPER_CAMEL_CASE | "FooBar"
        PropertyNamingStrategies.LOWER_CASE       | "foobar"
        PropertyNamingStrategies.LOWER_DOT_CASE   | "foo.bar"
    }

}
