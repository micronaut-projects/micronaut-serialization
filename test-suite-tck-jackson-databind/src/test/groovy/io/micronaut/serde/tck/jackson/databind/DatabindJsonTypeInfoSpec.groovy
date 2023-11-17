package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.JsonTypeInfoSpec
import spock.lang.PendingFeature

class DatabindJsonTypeInfoSpec extends JsonTypeInfoSpec {

    @Override
    protected boolean jacksonCustomOrder() {
        return true
    }

    @PendingFeature(reason = "Cannot define Creator property \"name\" as `@JsonUnwrapped`: combination not yet supported")
    def 'test JsonTypeInfo with wrapper array in constructor and @JsonUnwrapped'() {
        given:
            def ctx = buildContext('example.Wrapper', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Wrapper(Base base, String other, @JsonUnwrapped Name name) {
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
class Name {
    public String fieldX;
    public String fieldY;
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
            def wrapperClass = ctx.classLoader.loadClass('example.Wrapper')
            def name = newInstance(ctx, 'example.Name')
            name.fieldX = "X"
            name.fieldY = "Y"
            def a = newInstance(ctx, 'example.A')
            a.fieldA = 'foo'
            def wrapper = newInstance(ctx, 'example.Wrapper', a, "abc", name)

        expect:
            deserializeFromString(jsonMapper, wrapperClass, '{"base": ["a",{"fieldA":"foo"}], "other":"xyz"}').base.fieldA == 'foo'
            deserializeFromString(jsonMapper, wrapperClass, '{"base": ["b",{"fieldB":"foo"}], "other":"xyz"}').base.fieldB == 'foo'
            deserializeFromString(jsonMapper, wrapperClass, '{"base": ["c",{"fieldB":"foo"}], "other":"xyz"}').base.fieldB == 'foo'
            deserializeFromString(jsonMapper, wrapperClass, '{"base": ["c",{"fieldB":"foo"}], "other":"xyz","fieldX":"ABC"}').name.fieldX == 'ABC'

            serializeToString(jsonMapper, wrapper) == '{"base":["a",{"fieldA":"foo"}],"other":"abc","fieldX":"X","fieldY":"Y"}'

        cleanup:
            ctx.close()
    }
}
