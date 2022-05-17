package io.micronaut.serde.jackson.object

import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue

class ArraySerdeSpec extends JsonCompileSpec {
    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/196")
    def "serialize & deserialize object array - field"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Object[] data;
}
''', true)
        def test = newInstance(compiled, 'example.Test')
        test.data = ['foo', 'bar'] as Object[]

        def testClass = test.getClass()

        expect:
        jsonMapper.writeValueAsString(test) == '{"data":["foo","bar"]}'
        jsonMapper.readValue('{"data":["foo","bar"]}', argumentOf(compiled, 'example.Test')).data == ['foo', 'bar'] as Object[]

        cleanup:
        compiled.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/196")
    def "serialize & deserialize object array - constructor"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public final Object[] data;
    Test(Object[] data) {
        this.data = data;
    }
}
''', true)
        def test = newInstance(compiled, 'example.Test', ['foo', 'bar'] as Object[])

        def testClass = test.getClass()

        expect:
        jsonMapper.writeValueAsString(test) == '{"data":["foo","bar"]}'
        jsonMapper.readValue('{"data":["foo","bar"]}', argumentOf(compiled, 'example.Test')).data == ['foo', 'bar'] as Object[]

        cleanup:
        compiled.close()
    }
}