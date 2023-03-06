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
  public static Foo fromValue(String value) {
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

        expect:
        jsonMapper.writeValueAsString(test) == '{"data":"bz"}'
        jsonMapper.writeValueAsString(BAR) == '"br"'
        jsonMapper.writeValueAsString(BAT) == '"BT"'

        jsonMapper.readValue('{"data":"bz"}', argumentOf(compiled, 'enumtest.Test')).data.name() == "BAZ"
        jsonMapper.readValue('"br"', argumentOf(compiled, "enumtest.Foo")) == BAR
        jsonMapper.readValue('"BT"', argumentOf(compiled, "enumtest.Foo")) == BAT

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

        expect:
        jsonMapper.writeValueAsString(test) == '{"data":"BAZ"}'
        jsonMapper.writeValueAsString(BAR) == '"BAR"'

        jsonMapper.readValue('{"data":"BAR"}', argumentOf(compiled, 'enumtest.Test')).data == BAR
        jsonMapper.readValue('{"data":"baz"}', argumentOf(compiled, 'enumtest.Test')).data.name() == "BAZ"

        cleanup:
        compiled.close()
    }
}
