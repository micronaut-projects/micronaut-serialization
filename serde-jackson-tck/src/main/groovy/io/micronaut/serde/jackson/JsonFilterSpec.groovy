package io.micronaut.serde.jackson


import spock.lang.Unroll

import java.util.function.Predicate

abstract class JsonFilterSpec extends JsonCompileSpec {

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
        context.getBean(PredicateFilter).predicate = { it -> !Objects.equals(it, ignoredValue) }
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

    static interface PredicateFilter {

        void setPredicate(Predicate<Object> predicate);
    }
}
