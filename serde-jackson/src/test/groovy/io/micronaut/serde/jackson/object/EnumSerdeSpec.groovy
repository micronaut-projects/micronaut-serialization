package io.micronaut.serde.jackson.object

import io.micronaut.serde.AbstractJsonCompileSpec
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
import io.micronaut.core.annotation.Nullable;
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
}
