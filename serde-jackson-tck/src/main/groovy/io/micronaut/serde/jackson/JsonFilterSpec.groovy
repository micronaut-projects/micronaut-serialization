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
