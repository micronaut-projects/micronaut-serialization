package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * How the format features of a bean reach what the bean holds.
 */
class SerdeBeanFeaturesSpec extends JsonCompileSpec {

    private static final String SOURCE = '''
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;

@Serdeable
@JsonFormat(with = JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES)
record Sorted(Map<String, Integer> typed, Object any, List<Object> anyList, Map<String, Object> anyMap, Holder holder) {
}

@Serdeable
record Holder(Map<String, Integer> map) {
}
'''

    private static Map<String, Integer> unsorted() {
        Map<String, Integer> map = new LinkedHashMap<>()
        map.put('b', 1)
        map.put('a', 2)
        map
    }

    void "the features of a bean apply to a value that is written by its run-time type"() {
        given:
        def context = buildContext('test.Sorted', SOURCE, true, [:])
        def bean = newInstance(context, 'test.Sorted', [unsorted(), unsorted(), [unsorted()], [c: unsorted()], null] as Object[])

        expect:
        writeJson(jsonMapper, bean) == '{"typed":{"a":2,"b":1},"any":{"a":2,"b":1},"anyList":[{"a":2,"b":1}],' +
            '"anyMap":{"c":{"a":2,"b":1}}}'

        cleanup:
        context.close()
    }
}
