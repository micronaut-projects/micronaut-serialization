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
