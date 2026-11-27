package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

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

    @Unroll
    void "a bean is written with the features of the bean that holds it, and without them on its own, when #first is written first"() {
        given: 'run-time serializers, which keep the serializers of a bean between documents'
        def context = buildContext('test.Sorted', SOURCE, true, ['micronaut.serde.serialization.disable-generated-serializer': true])
        def holder = newInstance(context, 'test.Holder', [unsorted()] as Object[])
        def bean = newInstance(context, 'test.Sorted', [null, null, null, null, holder] as Object[])
        String held = '{"holder":{"map":{"a":2,"b":1}}}'
        String onItsOwn = '{"map":{"b":1,"a":2}}'

        expect:
        if (first == 'the held bean') {
            assert writeJson(jsonMapper, holder) == onItsOwn
            assert writeJson(jsonMapper, bean) == held
        } else {
            assert writeJson(jsonMapper, bean) == held
            assert writeJson(jsonMapper, holder) == onItsOwn
        }
        writeJson(jsonMapper, bean) == held
        writeJson(jsonMapper, holder) == onItsOwn

        cleanup:
        context.close()

        where:
        first << ['the held bean', 'the bean that holds it']
    }
}
