package io.micronaut.serde.jackson.object

import io.micronaut.serde.AbstractJsonCompileSpec
import spock.lang.Ignore
import spock.lang.Issue

class EnumSerdeSpec extends AbstractJsonCompileSpec {
    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/360")
    def "test enum with @JsonCreator"() {
        given:
        def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Foo data = Foo.BAZ;
}

@Serdeable
enum Foo {
  BAR("br"),
  BAZ("bz"),
  BAT("BT");

  private final String value;

  Foo(String value) {
    this.value = value;
  }

  @JsonCreator
  public static Foo fromValue(@Nullable String value) {
    return Arrays.stream(values())
        .filter(foo -> Objects.equals(foo.toString(), value))
        .findFirst().orElse(null);
  }

  @JsonValue
  public String toString() {
    return this.value;
  }

}
''')
        def test = newInstance(compiled, 'enumtest.Test')
        def BAR = getEnum(compiled, 'enumtest.Foo.BAR')
        def BAT = getEnum(compiled, 'enumtest.Foo.BAT')
        def enumClass = argumentOf(compiled, "enumtest.Foo")

        expect:
        jsonMapper.writeValueAsString(test) == '{"data":"bz"}'
        jsonMapper.writeValueAsString(BAR) == '"br"'
        jsonMapper.writeValueAsString(BAT) == '"BT"'

        jsonMapper.readValue('{"data":"bz"}', argumentOf(compiled, 'enumtest.Test')).data.name() == "BAZ"
        jsonMapper.readValue('"br"', enumClass) == BAR
        jsonMapper.readValue('"BT"', enumClass) == BAT
        jsonMapper.readValue('"unknown value"', enumClass) == null
        jsonMapper.readValue('null', enumClass) == null

        cleanup:
        compiled.close()
    }

    def "test default enum handling"() {
        given:
        def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Foo data = Foo.BAZ;
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
        def test = newInstance(compiled, 'enumtest.Test')
        def BAR = getEnum(compiled, "enumtest.Foo.BAR")
        def BAZ = getEnum(compiled, "enumtest.Foo.BAZ")
        def argument = argumentOf(compiled, 'enumtest.Test')

        expect:
        jsonMapper.writeValueAsString(test) == '{"data":"BAZ"}'
        jsonMapper.writeValueAsString(BAR) == '"BAR"'

        jsonMapper.readValue('{"data":"BAR"}', argument).data == BAR
        jsonMapper.readValue('{"data":"baz"}', argument).data.name() == "BAZ"
        jsonMapper.readValue('{"data":null}', argument).data == BAZ

        cleanup:
        compiled.close()
    }

    @Ignore
    def "test null value handling"() {
        given:
        def compiled = buildContext('''
package enumtest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

@Serdeable
enum EnumWithDefaultValue {
  MyValue("my value"),
  DefaultValue(null);

  private final String value;

  EnumWithDefaultValue(String value) {
    this.value = value;
  }

  @JsonCreator
  public static EnumWithDefaultValue fromValue(@Nullable String value) {
    return MyValue.toString().equals(value) ? MyValue : DefaultValue;
  }

  @JsonValue
  public String toString() {
    return this.value;
  }

}
''')
        def MyValue = getEnum(compiled, 'enumtest.EnumWithDefaultValue.MyValue')
        def DefaultValue = getEnum(compiled, 'enumtest.EnumWithDefaultValue.DefaultValue')
        def enumClass = argumentOf(compiled, 'enumtest.EnumWithDefaultValue')

        expect:
        jsonMapper.writeValueAsString(MyValue) == '"my value"'
        jsonMapper.writeValueAsString(DefaultValue) == 'null'

        jsonMapper.readValue('"my value"', enumClass) == MyValue
        jsonMapper.readValue('null', enumClass) == DefaultValue
        jsonMapper.readValue('"unknown value"', enumClass) == DefaultValue

        cleanup:
        compiled.close()
    }

    def 'test deserialize EnumSet for Enum with @JsonValue on property'() {
        given:
        def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.EnumSet;

@Serdeable
class Test {
    private EnumSet<MyEnum> enumSet;

    public EnumSet<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(EnumSet<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    @JsonValue
    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
''')
        when:
        def json = '{"enumSet":["value_1","value_3"]}'
        def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
        result.enumSet instanceof EnumSet
        result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
        context.close()
    }

    def 'test deserialize EnumSet for Enum with @JsonValue on getter'() {
        given:
        def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.serde.annotation.Serdeable;
import java.util.EnumSet;

@Serdeable
class Test {
    private EnumSet<MyEnum> enumSet;

    public EnumSet<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(EnumSet<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
''')
        when:
        def json = '{"enumSet":["value_1","value_3"]}'
        def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
        result.enumSet instanceof EnumSet
        result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
        context.close()
    }

    def 'test deserialize EnumSet for Enum with @JsonCreator'() {
        given:
        def context = buildContext('''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Objects;
import java.util.Arrays;
import java.util.EnumSet;

@Serdeable
class Test {
    private EnumSet<MyEnum> enumSet;

    public EnumSet<MyEnum> getEnumSet() {
        return enumSet;
    }

    public void setEnumSet(EnumSet<MyEnum> enumSet) {
        this.enumSet = enumSet;
    }
}

@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MyEnum create(String value) {
        return Arrays.stream(values())
            .filter(val -> Objects.equals(val.value, value))
            .findFirst()
            .orElse(null);
    }
}
''')
        when:
        def json = '{"enumSet":["value_1","value_3"]}'
        def result = jsonMapper.readValue(json, argumentOf(context, 'test.Test'))

        then:
        result.enumSet instanceof EnumSet
        result.enumSet == EnumSet.of(getEnum(context, 'test.MyEnum.VALUE1'), getEnum(context, 'test.MyEnum.VALUE3'))

        cleanup:
        context.close()
    }

    void "enum @JsonValue property"() throws IOException {
        given:
        def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Foo {
    private final MyEnum myEnum;

    @JsonCreator
    Foo(@JsonProperty("myEnum") MyEnum myEnum) {
        this.myEnum = myEnum;
    }

    public MyEnum getMyEnum() {
        return myEnum;
    }
}

@Serdeable
enum MyEnum {

    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");

    private final String value;

    MyEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
''')
        def enumValue2 = context.classLoader.loadClass('example.MyEnum').VALUE2
        def testBean = newInstance(context, 'example.Foo', enumValue2)
        when:
        String json = jsonMapper.writeValueAsString(testBean)
        then:
        '{"myEnum":"value_2"}' == json

        when:
        def foo = jsonMapper.readValue(json, testBean.class)

        then:
        foo.myEnum == enumValue2

        when:
        jsonMapper.readValue('{"myEnum":"invalid"}', testBean.class)
        then:
        thrown IOException
    }

    void "enum @JsonValue on field"() throws IOException {
        given:
        def context = buildContext('''
package example;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
@Serdeable
class Foo {
    private final MyEnum myEnum;
    @JsonCreator
    Foo(@JsonProperty("myEnum") MyEnum myEnum) {
        this.myEnum = myEnum;
    }
    public MyEnum getMyEnum() {
        return myEnum;
    }
}
@Serdeable
enum MyEnum {
    VALUE1("value_1"),
    VALUE2("value_2"),
    VALUE3("value_3");
    @JsonValue
    private final String value;
    MyEnum(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
''')
        def enumValue2 = context.classLoader.loadClass('example.MyEnum').VALUE2
        def testBean = newInstance(context, 'example.Foo', enumValue2)
        when:
        String json = jsonMapper.writeValueAsString(testBean)
        then:
        '{"myEnum":"value_2"}' == json

        when:
        def foo = jsonMapper.readValue(json, testBean.class)

        then:
        foo.myEnum == enumValue2

        when:
        jsonMapper.readValue('{"myEnum":"invalid"}', testBean.class)
        then:
        thrown IOException
    }
}
