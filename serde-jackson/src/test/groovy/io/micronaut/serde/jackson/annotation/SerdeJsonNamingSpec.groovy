package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.config.naming.IdentityStrategy
import io.micronaut.serde.config.naming.KebabCaseStrategy
import io.micronaut.serde.config.naming.LowerCamelCaseStrategy
import io.micronaut.serde.config.naming.LowerCaseStrategy
import io.micronaut.serde.config.naming.SnakeCaseStrategy
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy
import io.micronaut.serde.config.naming.UpperCamelCaseStrategyWithSpaces
import io.micronaut.serde.jackson.JsonNamingSpec
import spock.lang.Unroll

class SerdeJsonNamingSpec extends JsonNamingSpec {

    @Unroll
    void "test json naming strategy: #strategy"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable(naming = ${strategy.name}.class)
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
        strategy                         | name
        IdentityStrategy                 | "fooBar"
        KebabCaseStrategy                | "foo-bar"
        SnakeCaseStrategy                | "foo_bar"
        UpperCamelCaseStrategy           | "FooBar"
        LowerCamelCaseStrategy           | "fooBar"
        LowerCaseStrategy                | "foobar"
        UpperCamelCaseStrategyWithSpaces | "Foo Bar"
    }

    @Unroll
    void "test json naming strategy for records: #strategy"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable(naming = ${strategy.name}.class)
record Test(String fooBar) {}
""")
        beanUnderTest = typeUnderTest.type.newInstance("test")

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
        strategy                         | name
        IdentityStrategy                 | "fooBar"
        KebabCaseStrategy                | "foo-bar"
        SnakeCaseStrategy                | "foo_bar"
        UpperCamelCaseStrategy           | "FooBar"
        LowerCamelCaseStrategy           | "fooBar"
        LowerCaseStrategy                | "foobar"
        UpperCamelCaseStrategyWithSpaces | "Foo Bar"
    }
}
