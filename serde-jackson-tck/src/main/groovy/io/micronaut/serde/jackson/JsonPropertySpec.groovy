package io.micronaut.serde.jackson


import spock.lang.Unroll

class JsonPropertySpec extends JsonCompileSpec {

    void "simple JsonCreator"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator
    public Test(@JsonProperty("foo") String foo) {
        this.foo = foo;
    }
}

''')
            def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)

        expect:
            deserialized.foo == "42"

        cleanup:
            context.close()
    }

    void "static JsonCreator"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    private Test(@JsonProperty("foo") String foo) {
        this.foo = foo;
    }

    @JsonCreator
    public static Test creator(@JsonProperty("foo") String foo) {
        return new Test(foo + "xyz");
    }
}
''')
            def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)

        expect:
            deserialized.foo == "42xyz"

        cleanup:
            context.close()
    }

    void "static JsonCreator on interface"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
interface Test {

    String getFoo();

    @JsonCreator
    static Test creator(@JsonProperty("foo") String foo) {
        return (example.Test) () -> foo + "abc";
    }

}
''')
            def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)

        expect:
            deserialized.foo == "42abc"

        cleanup:
            context.close()
    }

    void "static JsonCreator(mode = JsonCreator.Mode.DELEGATING) on interface"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
interface Test {

    String getFoo();

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static Test creator(Data data) {
        return (example.Test) () -> data.foo() + "abc";
    }

}

@Serdeable
record Data(String foo) {
}
''')
            def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)

        expect:
            deserialized.foo == "42abc"

        cleanup:
            context.close()
    }

    void "test @JsonProperty.Access.WRITE_ONLY (set only) - records"() {
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty
    String value,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String ignored
) {}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == 'xyz'

        cleanup:
            context.close()
    }

    void "test @JsonProperty.Access.WRITE_ONLY (set only) - constructor"() {
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {

    private String value;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ignored;

    @JsonCreator
    public Test(@JsonProperty("value") String value, @JsonProperty("ignored") String ignored) {
        this.value = value;
        this.ignored = ignored;
    }

    public String getValue() {
        return this.value;
    }

    public String getIgnored() {
        return this.ignored;
    }

}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == 'xyz'

        cleanup:
            context.close()
    }


    @Unroll
    void "serde Number"(Number number) {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Number foo;
}
''', [:])
        beanUnderTest.@foo = number

        expect:
            jsonMapper.readValue(json, typeUnderTest).@foo == result
            writeJson(jsonMapper, beanUnderTest) == json

        cleanup:
            context.close()

        where:
            number                      || result                      || json
            123.456                     || 123.456                     || '{"foo":123.456}'
            Double.valueOf(123.123)     || Double.valueOf(123.123)     || '{"foo":123.123}'
            Float.valueOf(123.123)      || Double.valueOf(123.123)     || '{"foo":123.123}'
            BigDecimal.valueOf(123.123) || BigDecimal.valueOf(123.123) || '{"foo":123.123}'
            Integer.valueOf(123)        || Integer.valueOf(123)        || '{"foo":123}'
            Short.valueOf((short) 123)  || Short.valueOf((short) 123)  || '{"foo":123}'
            BigInteger.valueOf(123)     || BigInteger.valueOf(123)     || '{"foo":123}'

    }

    void "missing nullable properties are not overwritten"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @Nullable
    public String foo = "bar";
}
''')

        expect:
            jsonMapper.readValue('{}', typeUnderTest).foo == 'bar'
            jsonMapper.readValue('{"foo":null}', typeUnderTest).foo == null

        cleanup:
            context.close()
    }

    void "optional nullable mix"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @Nullable
    private String foo;

    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }

    @JsonSetter("foo")
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}
''', [:])
        beanUnderTest.foo = 'bar'

        expect:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest).foo.get() == 'bar'
        writeJson(jsonMapper, beanUnderTest) == '{"foo":"bar"}'

        cleanup:
        context.close()
    }

    void "creator with optional parameter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public final String foo;
    public final String bar;

    @JsonCreator
    public Test(@Nullable @JsonProperty("foo") String foo, @JsonProperty(value = "bar", required = true) String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        typeUnderTest = argumentOf(context, 'example.Test')

        expect:
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"foo":"123","bar":"456"}').foo == '123'
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"foo":"123","bar":"456"}').bar == '456'

        deserializeFromString(jsonMapper, typeUnderTest.type, '{"bar":"456"}').foo == null
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"bar":"456"}').bar == '456'

        when:
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"foo":"123"}')
        then:
        thrown Exception

        cleanup:
        context.close()
    }

    void "JsonProperty on field"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;import io.micronaut.core.annotation.Introspected;
@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonProperty("foo")
    public String bar;
}
''', [:])
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        beanUnderTest.bar = "42"
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonProperty on getter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
@io.micronaut.serde.annotation.Serdeable
class Test {
    private String bar;

    @JsonProperty("foo")
    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
}
''', [:])
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        beanUnderTest.bar = "42"
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonProperty on is-getter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private boolean bar;

    @JsonProperty("foo")
    public boolean isBar() {
        return bar;
    }

    public void setBar(boolean bar) {
        this.bar = bar;
    }
}
''', [:])
        def deserialized = jsonMapper.readValue('{"foo": true}', typeUnderTest)
        beanUnderTest.bar = true
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == true
        serialized == '{"foo":true}'

        cleanup:
        context.close()
    }

    void "JsonProperty on accessors without prefix"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String bar;

    @JsonProperty
    public String bar() {
        return bar;
    }

    @JsonProperty
    public void bar(String bar) {
        this.bar = bar;
    }
}
''', [:])
        def deserialized = jsonMapper.readValue('{"bar": "42"}', typeUnderTest)
        beanUnderTest.bar = "42"
        def serialized = serializeToString(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == "42"
        serialized == '{"bar":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator constructor"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty("foo")
    private final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator with parameter names"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;
    public final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") String foo, @JsonProperty("bar") String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42", "bar": "56"}', typeUnderTest)

        expect:
        deserialized.foo == "42"
        deserialized.bar == "56"

        cleanup:
        context.close()
    }

    void "JsonCreator constructor with properties mode set"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty("foo")
    private final String bar;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator static method"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty("foo")
    private final String bar;

    private Test(String bar) {
        this.bar = bar;
    }

    @JsonCreator
    public static Test create(@JsonProperty("foo") String bar) {
        return new Test(bar);
    }

    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator no getter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{}'

        cleanup:
        context.close()
    }

    void "test JsonProperty on private methods"() {
        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    private void setValueInternal(String value) {
        this.value = value.toLowerCase();
    }

    @JsonProperty("value")
    private String getValueInternal() {
        return value.toUpperCase();
    }
}
""", [value: 'test'])
        then:
        def e = thrown(RuntimeException)
        e.message.contains("JSON annotations cannot be used on private methods")
    }

    void "test JsonProperty on protected methods"() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonIgnore
    private String value;

    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    protected void setValueInternal(String value) {
        this.value = value.toLowerCase();
    }

    @JsonProperty("value")
    protected String getValueInternal() {
        return value.toUpperCase();
    }
}
""", [value: 'test'])
        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"value":"TEST"}'

        when:
        def bean =
                jsonMapper.readValue(result, argumentOf(context, 'test.Test'))
        then:
        bean.value == 'test'

        cleanup:
        context.close()
    }

    @Unroll
    void "test invalid defaultValue for #type and value #value"() {

        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(defaultValue = "$value")
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", [:])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid defaultValue [$value] specified")

        where:
        type    | value
        Integer | 'junk'
        URL     | 'ws://junk'
    }

    void "test optional by default primitive field"() {

        given:
        def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private int value = 5;

    public void setValue(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
}
""")

        when:
        def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then:
        bean.value == 5

        cleanup:
        ctx.close()
    }

    void "test optional by default primitive field in constructor"() {

        given:
        def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

@Serdeable
class Test {
    private final $type value;

    @com.fasterxml.jackson.annotation.JsonCreator
    Test(@JsonProperty("value") $type value) {
        this.value = value;
    }

    public $type getValue() {
        return value;
    }
}
""")

        when:
        def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then:
        bean.value == value

        cleanup:
        ctx.close()

        where:
        type      | value
        "byte"    | (byte) 0
        "short"   | (short) 0
        "int"     | 0
        "long"    | 0L
        "float"   | 0F
        "double"  | 0D

        "@Nullable Byte"    | null
        "@Nullable Short"   | null
        "@Nullable Integer" | null
        "@Nullable Long"    | null
        "@Nullable Float"   | null
        "@Nullable Double"  | null
    }

    @Unroll
    void "test invalid defaultValue for #type and value #value for records"() {

        when:
        buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty(defaultValue = "$value")
    $type.name value
) {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Invalid defaultValue [$value] specified")

        where:
        type      | value
        Integer   | 'junk'
        int.class | 'junk'
        URL       | 'ws://junk'
    }

}
