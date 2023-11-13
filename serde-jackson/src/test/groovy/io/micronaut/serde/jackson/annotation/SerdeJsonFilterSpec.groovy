package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.PropertyFilter
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonFilterSpec
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Unroll

import java.util.function.Predicate

class SerdeJsonFilterSpec extends JsonFilterSpec {

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

    @Named("ignore-value")
    @Singleton
    static class IgnoreValueFilter implements PropertyFilter, JsonFilterSpec.PredicateFilter {

        private Predicate<Object> predicate

        boolean shouldInclude(Serializer.EncoderContext context, Serializer<Object> ser, Object object, String name, Object val) {
            return predicate.test(val)
        }

        @Override
        void setPredicate(Predicate<Object> predicate) {
            this.predicate = predicate
        }
    }

}
