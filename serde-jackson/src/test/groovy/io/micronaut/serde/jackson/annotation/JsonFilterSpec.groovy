package io.micronaut.serde.jackson.annotation

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.serde.PropertyFilter
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec
import jakarta.inject.Named
import spock.lang.Requires
import spock.lang.Unroll
import jakarta.inject.Singleton


@Requires({ jvm.isJava17Compatible() })
class JsonFilterSpec extends JsonCompileSpec {

    @Unroll
    void "test @JsonFilter by boolean value for #value, (included = #include)"() {
        given:
        def context = buildContext("""
package jsonfilter;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFilter;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.PropertyFilter;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Serdeable
@JsonFilter("explicitly-set")
record Test (
    String value,
    boolean include
) {}

@Named("explicitly-set")
@Singleton
class ExplicitlySetFilter implements PropertyFilter {
    public boolean shouldInclude(Serializer.EncoderContext context, Serializer<Object> ser, Object object, String name, Object val) {
        if (object instanceof Test) {
            return name.equals("value") && ((Test) object).include();
        }
        return true;
    }
}
""")
        def bean = newInstance(context, 'jsonfilter.Test', value, include)
        String json = writeJson(jsonMapper, bean)

        expect:
        json == result

        cleanup:
        context.close()

        where:
        value   | include    | result
        null    | true       | '{"value":null}'
        null    | false      | '{}'
        "value" | true       | '{"value":"value"}'
        "value" | false      | '{}'
        ""      | true       | '{"value":""}'
        ""      | false      | '{}'
    }

    @Unroll
    void "test @JsonFilter ignore a value for #value, (ignore = #ignoredValue)"() {
        given:
        def context = buildContext("""
package jsonfilter;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFilter;
import java.util.List;

@Serdeable
@JsonFilter("ignore-value")
record Test (
    ${type} value
) {}
""")
        context.getBean(IgnoreValueFilter).ignoredValue = ignoredValue
        def bean = newInstance(context, 'jsonfilter.Test', value)
        String json = writeJson(jsonMapper, bean)

        expect:
        json == result

        cleanup:
        context.close()

        where:
        type           | ignoredValue | value       | result
        'String'       | null         | null        | '{}'
        'String'       | 'ignored'    | null        | '{"value":null}'
        'String'       | 'ignored'    | 'ignored'   | '{}'
        'List<String>' | ['ignored']  | ['ignored'] | '{}'
        'List<String>' | []           | ['a', 'b']  | '{"value":["a","b"]}'
        'List<String>' | null         | []          | '{"value":[]}'
    }

    void "test @JsonFilter works with @JsonProperty correctly"() {
        given:
        def context = buildContext("""
package jsonfilter;

import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.Serializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFilter;
import jakarta.inject.Singleton;
import jakarta.inject.Named;

@Serdeable
@JsonFilter("my-filter")
record TestModel (
    String value,
    @JsonProperty("second-value")
    String value2
) {}

@Singleton
@Named("my-filter")
class MyFilter implements PropertyFilter {
    public boolean shouldInclude(Serializer.EncoderContext context, Serializer<Object> ser, Object bean, String name, Object val) {
        if (bean instanceof TestModel) {
            return name.equals("value") || name.equals("value2");
        }
        return true;
    }
}
""")
        def bean = newInstance(context, 'jsonfilter.TestModel', null, null)

        when:
        var json = writeJson(jsonMapper, bean)

        then:
        '{"value":null,"second-value":null}' == json
    }

    void "test @JsonFilter throws error when filter not defined"() {
        given:
        def context = buildContext("""
package jsonfilter;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFilter;

@Serdeable
@JsonFilter("non-existing-filter")
record TestModel (
    String value
) {}
""")
        def bean = newInstance(context, 'jsonfilter.TestModel', "value")

        when:
        writeJson(jsonMapper, bean)

        then:
        thrown(ConfigurationException)
    }

    @Named("ignore-value")
    @Singleton
    static class IgnoreValueFilter implements PropertyFilter {
        Object ignoredValue

        boolean shouldInclude(Serializer.EncoderContext context, Serializer<Object> ser, Object object, String name, Object val) {
            return val != ignoredValue
        }
    }
}
