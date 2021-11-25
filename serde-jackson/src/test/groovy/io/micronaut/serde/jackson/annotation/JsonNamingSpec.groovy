package io.micronaut.serde.jackson.annotation

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.micronaut.serde.config.naming.IdentityStrategy
import io.micronaut.serde.config.naming.KebabCaseStrategy
import io.micronaut.serde.config.naming.LowerCamelCaseStrategy
import io.micronaut.serde.config.naming.LowerCaseStrategy
import io.micronaut.serde.config.naming.SnakeCaseStrategy
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy
import io.micronaut.serde.config.naming.UpperCamelCaseStrategyWithSpaces
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Requires
import spock.lang.Unroll

class JsonNamingSpec extends JsonCompileSpec {
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
    void "test json naming strategy jackson databind: #strategy"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
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

    @Unroll
    @Requires({ jvm.isJava17Compatible() })
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
