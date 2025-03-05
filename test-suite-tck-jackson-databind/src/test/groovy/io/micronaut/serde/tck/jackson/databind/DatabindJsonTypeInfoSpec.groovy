package io.micronaut.serde.tck.jackson.databind

import io.micronaut.serde.jackson.JsonTypeInfoSpec
import spock.lang.PendingFeature

class DatabindJsonTypeInfoSpec extends JsonTypeInfoSpec {

    @Override
    protected boolean jacksonCustomOrder() {
        return true
    }

    @PendingFeature
    def 'test JsonTypeInfo with deduction unwrapped'() {
        given:
            def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A1.class),
    @JsonSubTypes.Type(value = B1.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base1 {
    @JsonUnwrapped public Base2 base2;
}
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A1 extends Base1 {
    public String fieldA1;
}
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B1 extends Base1 {
    public String fieldB1;
}

@JsonSubTypes({
    @JsonSubTypes.Type(value = A2.class),
    @JsonSubTypes.Type(value = B2.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base2 {
    public String sup;
}
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A2 extends Base2 {
    public String fieldA2;
}
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B2 extends Base2 {
    public String fieldB2;
}
''', true)
            def baseClass = compiled.classLoader.loadClass('example.Base1')
            def parsed = deserializeFromString(jsonMapper, baseClass, '{"fieldA1":"foo","sup":"x","fieldA2":"bar"}')

            def a1 = newInstance(compiled, 'example.A1')
            a1.fieldA1 = 'foo'
            def a2 = newInstance(compiled, 'example.A2')
            a2.sup = 'x'
            a2.fieldA2 = 'bar'
            a1.base2 = a2

        expect:
            parsed.fieldA1 == 'foo'
            parsed.base2.sup == 'x'
            parsed.base2.fieldA2 == 'bar'

            serializeToString(jsonMapper, a1) == '{"fieldA1":"foo","fieldA2":"bar","sup":"x"}'

        cleanup:
            compiled.close()
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
