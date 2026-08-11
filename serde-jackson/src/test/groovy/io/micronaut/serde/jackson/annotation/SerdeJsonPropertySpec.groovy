package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonPropertySpec
import spock.lang.PendingFeature

class SerdeJsonPropertySpec extends JsonPropertySpec {

    protected void assertSpecificSerdeSelection(ApplicationContext context,
                                                String className,
                                                boolean serializerGenerated,
                                                boolean deserializerGenerated) {
        Class<?> beanType = context.classLoader.loadClass(className)
        def type = Argument.of(beanType)
        def registry = context.getBean(SerdeRegistry)
        def specificSerializer = registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type)
        def specificDeserializer = registry.findDeserializer(type).createSpecific(registry.newDecoderContext(Object), type)
        if (specificSerializer.respondsTo('getSerializer')) {
            specificSerializer = specificSerializer.getSerializer()
        }
        if (specificDeserializer.respondsTo('getDeserializer')) {
            specificDeserializer = specificDeserializer.getDeserializer()
        }

        assert (specificSerializer.class.name == generatedClassName(beanType, 'Serializer')) == serializerGenerated
        assert (specificDeserializer.class.name == generatedClassName(beanType, 'Deserializer')) == deserializerGenerated
    }

    protected boolean validatesGeneratedSerdeSelection() {
        true
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name
        if (packageName) {
            localName = localName.substring(packageName.length() + 1)
        }
        "${packageName ? packageName + '.' : ''}Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }

    def "test explicit null handling - field"() {
        given:
            def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Foo data = Foo.BAZ;
    public String val = "sss";
}

@Serdeable
enum Foo {
  BAR("br"),
  BAZ("bz");

  private final String value;

  Foo(String value) {
    this.value = value;
  }
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            def bean = jsonMapper.readValue('{"data":null, "val":null}', argument)
        then:
            bean.data == null
            bean.val == null

        cleanup:
            compiled.close()
    }

    def "test explicit null handling - property"() {
        given:
            def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
class Test {
    public Foo data = Foo.BAZ;
    public String val = "sss";

    public Foo getData() {
        return data;
    }

    public void setData(Foo data) {
        this.data = data;
    }

    public String getVal() {
        return val;
    }

    public void setVal(String val) {
        this.val = val;
    }
}

@Serdeable
enum Foo {
  BAR("br"),
  BAZ("bz");

  private final String value;

  Foo(String value) {
    this.value = value;
  }
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            def bean = jsonMapper.readValue('{"data":null, "val":null}', argument)
        then:
            bean.data == null
            bean.val == null

        cleanup:
            compiled.close()
    }

    def "test explicit null for non-null field fails"() {
        given:
            def compiled = buildContext('''
package enumtest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @NonNull
    public String val = "sss";
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            jsonMapper.readValue('{"val":null}', argument)
        then:
            def e = thrown(Exception)
            e.message.contains("Non-null property [String val] is null in the supplied data")

        cleanup:
            compiled.close()
    }

    def "test explicit null for primitive field fails by default"() {
        given:
            def compiled = buildContext('''
package enumtest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public int val = 10;
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            jsonMapper.readValue('{"val":null}', argument)
        then:
            def e = thrown(Exception)
            e.message.contains("is null in the supplied data")

        cleanup:
            compiled.close()
    }

    def "test explicit null for primitive field keeps initialized value when configured"() {
        given:
            def compiled = buildContext('enumtest.Test', '''
package enumtest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public int val = 10;
}
''', true, [
                "micronaut.serde.deserialization.fail-on-null-for-primitives": false
            ])
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            def bean = jsonMapper.readValue('{"val":null}', argument)
        then:
            bean.val == 10

        cleanup:
            compiled.close()
    }

    def "test default null handling - field"() {
        given:
            def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @Nullable
    public Foo data = Foo.BAZ;
    @Nullable
    public String val = "sss";
}

@Serdeable
enum Foo {
  BAR("br"),
  BAZ("bz");

  private final String value;

  Foo(String value) {
    this.value = value;
  }
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            def bean = jsonMapper.readValue('{"data":null, "val":null}', argument)
        then:
            bean.data == null
            bean.val == null

        cleanup:
            compiled.close()
    }

    def "test default null handling - property"() {
        given:
            def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
class Test {
    @Nullable
    public Foo data = Foo.BAZ;
    @Nullable
    public String val = "sss";

    public Foo getData() {
        return data;
    }

    public void setData(Foo data) {
        this.data = data;
    }

    public String getVal() {
        return val;
    }

    public void setVal(String val) {
        this.val = val;
    }
}

@Serdeable
enum Foo {
  BAR("br"),
  BAZ("bz");

  private final String value;

  Foo(String value) {
    this.value = value;
  }
}
''')
            def argument = argumentOf(compiled, 'enumtest.Test')

        when:
            def bean = jsonMapper.readValue('{"data":null, "val":null}', argument)
        then:
            bean.data == null
            bean.val == null

        cleanup:
            compiled.close()
    }

    void "test @JsonProperty.Access.READ_ONLY (get only) - constructor"() {
        // Jackson cannot deserialize READ_ONLY as null
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {

    private String value;
    private String ignored;

    @JsonCreator
    public Test(@JsonProperty("value") String value, @JsonProperty(value = "ignored", access = JsonProperty.Access.READ_ONLY) String ignored) {
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
            result == '{"value":"test","ignored":"xyz"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == null

        cleanup:
            context.close()
    }

    void "test @JsonProperty.Access.READ_ONLY (get only) - record"() {
        // Jackson cannot deserialize READ_ONLY as null
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String ignored
) {}
""")
        when:
            def bean = newInstance(context, 'test.Test', "test", "xyz")
            def result = writeJson(jsonMapper, bean)

        then:
            result == '{"value":"test","ignored":"xyz"}'

        when:
            bean = jsonMapper.readValue('{"value":"test","ignored":"xyz"}', argumentOf(context, 'test.Test'))

        then:
            bean.value == 'test'
            bean.ignored == null

        cleanup:
            context.close()
    }

    void "test optional by default primitive field in constructor XXX"() {

        given:
            def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

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

    void "implicit creator with parameter names"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;
    public final String bar;

    public Test(String foo, String bar) {
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

    @PendingFeature(reason = 'single-parameter json creator. Dont think we should support this, can be done with delegating mode for JsonCreator')
    void "JsonCreator with single parameter of different name"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator
    public Test(String bar) {
        this.foo = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('"42"', typeUnderTest)

        expect:
        deserialized.foo == "42"

        cleanup:
        context.close()
    }

    void "test required primitive field"() {
        given:
        def ctx = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(required = true)
    private int value;

    @JsonCreator
    Test(@JsonProperty("value") int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
""")

        when:
        def bean = jsonMapper.readValue('{}', argumentOf(ctx, 'test.Test'))
        then: "Jackson will deserialize a default value"
        def e = thrown(Exception)
        e.message.contains("Unable to deserialize type [test.Test]. Required constructor parameter [int value] at index [0] is not present or is null in the supplied data")

        cleanup:
        ctx.close()
    }

    void "test JsonProperty isRequired takes precedence over required"() {
        given:
        def ctx = buildContext('test.Test', '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(required = false, isRequired = OptBoolean.TRUE)
    private int requiredValue;

    @JsonProperty(required = true, isRequired = OptBoolean.FALSE)
    private int optionalValue;

    @JsonCreator
    Test(@JsonProperty("requiredValue") int requiredValue,
         @JsonProperty("optionalValue") int optionalValue) {
        this.requiredValue = requiredValue;
        this.optionalValue = optionalValue;
    }

    public int getRequiredValue() {
        return requiredValue;
    }

    public int getOptionalValue() {
        return optionalValue;
    }
}
''')

        when: "the explicitly optional property is absent"
        def bean = jsonMapper.readValue('{"requiredValue":1}', argumentOf(ctx, 'test.Test'))

        then:
        bean.requiredValue == 1
        bean.optionalValue == 0

        when: "the explicitly required property is absent"
        jsonMapper.readValue('{"optionalValue":2}', argumentOf(ctx, 'test.Test'))

        then:
        def e = thrown(Exception)
        e.message.contains("Required constructor parameter")

        cleanup:
        ctx.close()
    }

    void "test @JsonProperty on field"() {
        // Jackson is using 'defaultValue' only for documentation
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty(value = "other", defaultValue = "default")
    private String value;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }

    public void setIgnored(boolean b) {
        this.ignored = b;
    }

    public boolean isIgnored() {
        return ignored;
    }
}
""", [value: 'test'])
        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"other":"test","ignored":false}'

        when:
        def bean = jsonMapper.readValue(result, argumentOf(context, 'test.Test'))
        then:
        bean.ignored == false
        bean.value == 'test'

        when:
        bean = jsonMapper.readValue("{}", argumentOf(context, 'test.Test'))
        then:
        bean.ignored == false
        bean.value == 'default'

        cleanup:
        context.close()

    }

    void "test @JsonProperty records"() {
        // Jackson is using 'defaultValue' only for documentation
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Test(
    @JsonProperty(value = "other", defaultValue = "default")
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, defaultValue = "false") // Get only
    boolean ignored
) {}
""")
        when:
        def bean = newInstance(context, 'test.Test', "test", false)
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"other":"test","ignored":false}'

        when:
        bean = jsonMapper.readValue('{"other":"test","ignored":true}', argumentOf(context, 'test.Test'))

        then:
        bean.ignored == false
        bean.value == 'test'

        when:
        bean = jsonMapper.readValue('{}', argumentOf(context, 'test.Test'))

        then:
        bean.ignored == false
        bean.value == 'default'

        cleanup:
        context.close()

    }

    void "test @JsonProperty records - invalid default value"() {
        // Jackson is using 'defaultValue' only for documentation
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable(validate = false)
record Test(
    @JsonProperty(value = "other", defaultValue = "default")
    String value,
    @JsonProperty(access = JsonProperty.Access.READ_ONLY, defaultValue = "junk")
    int number
) {}
""")
        when:
        def bean = newInstance(context, 'test.Test', "test", 10)
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"other":"test","number":10}'

        when:
        jsonMapper.readValue('{}', argumentOf(context, 'test.Test'))

        then:
        def e = thrown(IntrospectionException)
        e.cause.message.contains("Constructor Argument [int number] of type [test.Test] defines an invalid default value")

        cleanup:
        context.close()
    }

}
