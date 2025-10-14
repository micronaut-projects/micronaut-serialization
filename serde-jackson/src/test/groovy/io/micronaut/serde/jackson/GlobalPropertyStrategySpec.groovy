package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import spock.lang.Specification

class GlobalPropertyStrategySpec extends Specification {

    def 'test default global property strategy'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:
        def val = mapper.writeValueAsString(new MyBean("hello", 123))
        then:
        val == '{"fooBar":"hello","abcXyz":123}'

        when:
        def bean = mapper.readValue(val, MyBean)

        then:
        bean.fooBar() == "hello"
        bean.abcXyz() == 123

        cleanup:
        ctx.close()
    }

    def 'test global property strategy SNAKE_CASE'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.property-naming-strategy': "SNAKE_CASE"])
        def mapper = ctx.getBean(JsonMapper)

        when:
        def val = mapper.writeValueAsString(new MyBean("hello", 123))
        then:
        val == '{"foo_bar":"hello","abc_xyz":123}'

        when:
        def bean = mapper.readValue(val, MyBean)

        then:
        bean.fooBar() == "hello"
        bean.abcXyz() == 123

        cleanup:
        ctx.close()
    }

    def 'test global property strategy SNAKE_CASE with JsonProperty annotated fields'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.property-naming-strategy': "SNAKE_CASE"])
        def mapper = ctx.getBean(JsonMapper)

        when:
        def val = mapper.writeValueAsString(new MyBeanWithExplicitlyNamedProperties("hello", 123))
        then:
        val == '{"explicit_foo_bar_prop_name":"hello","abc_xyz":123}'

        when:
        def bean = mapper.readValue(val, MyBeanWithExplicitlyNamedProperties)

        then:
        bean.fooBar() == "hello"
        bean.abcXyz() == 123

        cleanup:
        ctx.close()
    }

    def 'test global property strategy LOWER_DOT_CASE'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.property-naming-strategy': "LOWER_DOT_CASE"])
        def mapper = ctx.getBean(JsonMapper)

        when:
        def val = mapper.writeValueAsString(new MyBean("hello", 123))
        then:
        val == '{"foo.bar":"hello","abc.xyz":123}'

        when:
        def bean = mapper.readValue(val, MyBean)

        then:
        bean.fooBar() == "hello"
        bean.abcXyz() == 123

        cleanup:
        ctx.close()
    }

}
