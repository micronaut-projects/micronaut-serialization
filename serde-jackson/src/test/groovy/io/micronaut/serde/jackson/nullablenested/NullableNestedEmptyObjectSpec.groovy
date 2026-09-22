package io.micronaut.serde.jackson.nullablenested

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper as DatabindJsonMapper

/**
 * An explicit empty object decodes into a non-null instance even when the property is nullable,
 * while an all-null {@code @JsonUnwrapped} bean still honours the nullability of the unwrapped property.
 */
class NullableNestedEmptyObjectSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext generatedContext = ApplicationContext.run()

    @Shared
    @AutoCleanup
    ApplicationContext runtimeContext = ApplicationContext.run(['micronaut.serde.deserialization.disable-generated-deserializer': true])

    @Shared
    DatabindJsonMapper databind = DatabindJsonMapper.builder().build()

    @Unroll
    void 'test a nullable nested bean reads #json on every stack'() {
        when:
        def generated = generatedContext.getBean(JsonMapper).readValue(json, NullableNestedParent)
        def runtime = runtimeContext.getBean(JsonMapper).readValue(json, NullableNestedParent)
        def databindValue = databind.readValue(json, NullableNestedParent)

        then:
        generated == expected
        runtime == expected
        databindValue == expected

        where:
        json                                              | expected
        '{"name":"n","user_info":{}}'                     | new NullableNestedParent('n', new NullableNestedUserInfo(null, null))
        '{"name":"n","user_info":{"job_code":"x"}}'       | new NullableNestedParent('n', new NullableNestedUserInfo('x', null))
        '{"name":"n","user_info":null}'                   | new NullableNestedParent('n', null)
        '{"name":"n"}'                                    | new NullableNestedParent('n', null)
    }

    @Unroll
    void 'test a non-nullable unwrapped bean inside a nullable setter bean reads #json on every stack'() {
        when:
        def generated = generatedContext.getBean(JsonMapper).readValue(json, UnwrappedSetterParent)
        def runtime = runtimeContext.getBean(JsonMapper).readValue(json, UnwrappedSetterParent)
        def databindValue = databind.readValue(json, UnwrappedSetterParent)

        then:
        [generated, runtime, databindValue].every { describe(it) == expected }

        where:
        json                                   | expected
        '{"outer":{"id":"x"}}'                 | [id: 'x', first: null, last: null]
        '{"outer":{}}'                         | [id: null, first: null, last: null]
        '{"outer":{"id":"x","first":"a"}}'     | [id: 'x', first: 'a', last: null]
    }

    private static Map describe(UnwrappedSetterParent parent) {
        def outer = parent.outer
        assert outer != null
        assert outer.inner != null
        [id: outer.id, first: outer.inner.first(), last: outer.inner.last()]
    }
}
